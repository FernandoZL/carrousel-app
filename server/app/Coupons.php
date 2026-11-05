<?php
declare(strict_types=1);

/**
 * Cupones. Cumpleaños: uno por festejado y año, emitido para el próximo cumpleaños cuya ventana no ha terminado,
 * solo cuando administración configuró el regalo. Oferta: uno por cuenta, a pedido, mientras la oferta está vigente.
 * Cada cupón copia el regalo, las condiciones y la vigencia al emitirse, y se canjea una sola vez en cualquier parque.
 */
final class Coupons
{
    private const ALPHABET='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    private const SELECT='SELECT c.*,m.name member_name,o.title offer_title,p.name redeemed_park,a.email account_email,a.name account_name FROM coupons c JOIN accounts a ON a.id=c.account_id LEFT JOIN members m ON m.id=c.member_id LEFT JOIN offers o ON o.id=c.offer_id LEFT JOIN parks p ON p.id=c.redeemed_park_id';
    private DateTimeImmutable $today;
    public function __construct(private PDO $db,?DateTimeImmutable $today=null) { $this->today=($today??new DateTimeImmutable())->setTime(0,0); }
    public function settings(): array
    {
        $s=array_column($this->db->query('SELECT setting_key,setting_value FROM settings')->fetchAll(),'setting_value','setting_key');
        return ['birthday_label'=>$s['birthday_label']??'','birthday_terms'=>$s['birthday_terms']??'','birthday_days_before'=>(int)($s['birthday_days_before']??15),'birthday_days_after'=>(int)($s['birthday_days_after']??14)];
    }
    public function saveSettings(array $staff,array $p): array
    {
        $label=Core::text($p['birthday_label']??'',250,'regalo',false);$terms=Core::longText($p['birthday_terms']??'',2000,'condiciones',false);
        $values=['birthday_label'=>$label,'birthday_terms'=>$terms];
        foreach(['birthday_days_before','birthday_days_after'] as $key){$v=filter_var($p[$key]??null,FILTER_VALIDATE_INT,['options'=>['min_range'=>0,'max_range'=>60]]);if($v===false)throw new DomainException('Los días de vigencia deben estar entre 0 y 60.');$values[$key]=(string)$v;}
        $q=$this->db->prepare('INSERT INTO settings(setting_key,setting_value,updated_at) VALUES(?,?,NOW()) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_at=NOW()');
        foreach($values as $k=>$v)$q->execute([$k,$v]);
        Core::audit($this->db,'staff',$staff['id'],'SETTINGS_SAVED','settings',null,$values);
        return $this->settings();
    }
    private function code(): string
    {
        for($i=0;$i<20;$i++){
            $code='';for($j=0;$j<6;$j++)$code.=self::ALPHABET[random_int(0,35)];
            $q=$this->db->prepare('SELECT 1 FROM coupons WHERE code=?');$q->execute([$code]);if(!$q->fetchColumn())return $code;
        }
        throw new RuntimeException('No se pudo generar un código único.');
    }
    /** Cumpleaños del año $year; el 29 de febrero se celebra el 28 en años no bisiestos. */
    private static function birthday(string $birthDate,int $year): DateTimeImmutable
    {
        [,$m,$d]=array_map('intval',explode('-',$birthDate));if($m===2&&$d===29&&!checkdate(2,29,$year))$d=28;
        return new DateTimeImmutable(sprintf('%04d-%02d-%02d',$year,$m,$d));
    }
    /** Emite los cupones de cumpleaños pendientes de la cuenta. Devuelve cuántos emitió. */
    public function issueBirthdays(int $account): int
    {
        $s=$this->settings();if($s['birthday_label']==='')return 0;
        $q=$this->db->prepare('SELECT id,birth_date FROM members WHERE account_id=?');$q->execute([$account]);$issued=0;
        foreach($q->fetchAll() as $m){
            $year=(int)$this->today->format('Y');$day=self::birthday($m['birth_date'],$year);
            if($this->today>$day->modify('+'.$s['birthday_days_after'].' days')){$year++;$day=self::birthday($m['birth_date'],$year);}
            if($year<=(int)substr($m['birth_date'],0,4))continue;
            $issued+=Core::transaction($this->db,function()use($account,$m,$year,$day,$s){
                $this->db->prepare('SELECT id FROM members WHERE id=? FOR UPDATE')->execute([$m['id']]);
                $q=$this->db->prepare('SELECT 1 FROM coupons WHERE member_id=? AND birthday_year=?');$q->execute([$m['id'],$year]);if($q->fetchColumn())return 0;
                $from=$day->modify('-'.$s['birthday_days_before'].' days')->format('Y-m-d');$until=$day->modify('+'.$s['birthday_days_after'].' days')->format('Y-m-d');
                $this->db->prepare("INSERT INTO coupons(kind,account_id,member_id,birthday_year,code,label,terms,valid_from,valid_until,created_at) VALUES('birthday',?,?,?,?,?,?,?,?,NOW())")
                    ->execute([$account,$m['id'],$year,$this->code(),$s['birthday_label'],$s['birthday_terms'],$from,$until]);
                Core::audit($this->db,'system',null,'BIRTHDAY_COUPON_ISSUED','coupon',(int)$this->db->lastInsertId(),['member_id'=>(int)$m['id'],'year'=>$year,'valid_from'=>$from,'valid_until'=>$until]);
                return 1;
            });
        }
        return $issued;
    }
    /** Cupón de una oferta vigente con cupón; si la cuenta ya lo tiene, devuelve el mismo. */
    public function claimOffer(int $account,int $offerId): array
    {
        $id=Core::transaction($this->db,function()use($account,$offerId){
            $today=$this->today->format('Y-m-d');
            $q=$this->db->prepare('SELECT * FROM offers WHERE id=? AND active=1 FOR UPDATE');$q->execute([$offerId]);$o=$q->fetch();
            if(!$o||!(int)$o['coupon_enabled'])throw new DomainException('Esta oferta no tiene cupón.',404);
            $q=$this->db->prepare('SELECT id FROM coupons WHERE offer_id=? AND account_id=?');$q->execute([$offerId,$account]);if($id=$q->fetchColumn())return (int)$id;
            if($today<$o['starts_on']||$today>$o['ends_on'])throw new DomainException('Esta oferta no está vigente.');
            $this->db->prepare("INSERT INTO coupons(kind,account_id,offer_id,code,label,terms,valid_from,valid_until,created_at) VALUES('offer',?,?,?,?,?,?,?,NOW())")
                ->execute([$account,$offerId,$this->code(),$o['coupon_label'],(string)$o['coupon_terms'],$today,$o['ends_on']]);
            $id=(int)$this->db->lastInsertId();Core::audit($this->db,'account',$account,'OFFER_COUPON_ISSUED','coupon',$id,['offer_id'=>$offerId]);return $id;
        });
        return $this->forAccount($account,$id);
    }
    private function status(array $c): array
    {
        $today=$this->today->format('Y-m-d');
        $c['status']=$c['voided_at']?'void':($c['redeemed_at']?'redeemed':($today<$c['valid_from']?'upcoming':($today>$c['valid_until']?'expired':'available')));
        return $c;
    }
    private static function publicFields(array $c): array
    {
        return ['id'=>(int)$c['id'],'kind'=>$c['kind'],'code'=>$c['code'],'label'=>$c['label'],'terms'=>$c['terms'],'valid_from'=>$c['valid_from'],'valid_until'=>$c['valid_until'],
            'status'=>$c['status'],'member_name'=>$c['member_name'],'offer_id'=>$c['offer_id']===null?null:(int)$c['offer_id'],'offer_title'=>$c['offer_title'],
            'birthday_year'=>$c['birthday_year']===null?null:(int)$c['birthday_year'],'redeemed_at'=>$c['redeemed_at'],'redeemed_park'=>$c['redeemed_park']];
    }
    /** Cupones de la cuenta cuya vigencia terminó hace menos de un año, los más recientes primero. */
    public function listForAccount(int $account): array
    {
        $this->issueBirthdays($account);
        $q=$this->db->prepare(self::SELECT.' WHERE c.account_id=? AND c.valid_until>=DATE_SUB(?,INTERVAL 365 DAY) ORDER BY c.valid_from DESC,c.id DESC LIMIT 100');
        $q->execute([$account,$this->today->format('Y-m-d')]);
        return array_map(fn($c)=>self::publicFields($this->status($c)),$q->fetchAll());
    }
    private function forAccount(int $account,int $id): array
    {
        $q=$this->db->prepare(self::SELECT.' WHERE c.id=? AND c.account_id=?');$q->execute([$id,$account]);return self::publicFields($this->status($q->fetch()));
    }
    private static function normalize(mixed $code): string
    {
        $code=strtoupper(trim((string)$code));if(!preg_match('/^[A-Z0-9]{6}$/',$code))throw new DomainException('El código tiene 6 letras o números.');
        return $code;
    }
    /** Consulta del personal: incluye a quién pertenece para que el cajero confirme antes de canjear. */
    public function lookup(mixed $code): array
    {
        $q=$this->db->prepare(self::SELECT.' WHERE c.code=?');$q->execute([self::normalize($code)]);$c=$q->fetch();
        if(!$c)throw new DomainException('No encontramos ese código.',404);
        $c=$this->status($c);return self::publicFields($c)+['account_email'=>$c['account_email'],'account_name'=>$c['account_name']];
    }
    /** Canje atómico: solo un parque puede canjear el código, aunque dos lo intenten a la vez. */
    public function redeem(array $staff,mixed $code): array
    {
        $code=self::normalize($code);
        if(!$staff['park_id'])throw new DomainException('Tu cuenta necesita un parque asignado para canjear.',403);
        Core::transaction($this->db,function()use($staff,$code){
            $q=$this->db->prepare('SELECT * FROM coupons WHERE code=? FOR UPDATE');$q->execute([$code]);$c=$q->fetch();
            if(!$c)throw new DomainException('No encontramos ese código.',404);
            $c=$this->status($c);
            $messages=['void'=>'Este cupón fue anulado.','redeemed'=>'Este cupón ya fue canjeado.','upcoming'=>'Este cupón todavía no está disponible.','expired'=>'Este cupón ya venció.'];
            if(isset($messages[$c['status']]))throw new DomainException($messages[$c['status']],409);
            $this->db->prepare('UPDATE coupons SET redeemed_at=NOW(),redeemed_by=?,redeemed_park_id=? WHERE id=? AND redeemed_at IS NULL AND voided_at IS NULL')->execute([$staff['id'],$staff['park_id'],$c['id']]);
            Core::audit($this->db,'staff',$staff['id'],'COUPON_REDEEMED','coupon',(int)$c['id'],['park_id'=>$staff['park_id'],'label'=>$c['label']]);
        });
        return $this->lookup($code);
    }
    public function void(array $staff,mixed $code,mixed $reason): array
    {
        $code=self::normalize($code);$reason=Core::text($reason,250,'motivo');
        Core::transaction($this->db,function()use($staff,$code,$reason){
            $q=$this->db->prepare('SELECT id,redeemed_at,voided_at FROM coupons WHERE code=? FOR UPDATE');$q->execute([$code]);$c=$q->fetch();
            if(!$c)throw new DomainException('No encontramos ese código.',404);
            if($c['redeemed_at']||$c['voided_at'])throw new DomainException('Solo se anula un cupón sin canjear.',409);
            $this->db->prepare('UPDATE coupons SET voided_at=NOW(),voided_by=?,void_reason=? WHERE id=?')->execute([$staff['id'],$reason,$c['id']]);
            Core::audit($this->db,'staff',$staff['id'],'COUPON_VOIDED','coupon',(int)$c['id'],['reason'=>$reason]);
        });
        return $this->lookup($code);
    }
}

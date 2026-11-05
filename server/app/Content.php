<?php
declare(strict_types=1);

/** Parques, ofertas y eventos: la app los lee; administración los crea y edita desde el panel. */
final class Content
{
    public function __construct(private PDO $db,private DateTimeImmutable $now=new DateTimeImmutable()) {}
    private static function park(array $r): array
    {
        return ['id'=>(int)$r['id'],'name'=>$r['name'],'address'=>$r['address'],'hours'=>$r['hours'],'phone'=>$r['phone'],
            'latitude'=>$r['latitude']===null?null:(float)$r['latitude'],'longitude'=>$r['longitude']===null?null:(float)$r['longitude'],'active'=>(bool)$r['active']];
    }
    public function parks(bool $all=false): array
    {
        return array_map([self::class,'park'],$this->db->query('SELECT * FROM parks'.($all?'':' WHERE active=1').' ORDER BY name')->fetchAll());
    }
    public function savePark(array $staff,array $p): array
    {
        $id=(int)($p['id']??0);$name=Core::text($p['name']??'',120,'nombre');$address=Core::text($p['address']??'',250,'dirección',false);
        $hours=Core::text($p['hours']??'',250,'horario',false);$phone=Core::text($p['phone']??'',30,'teléfono',false);
        $coords=[];foreach(['latitude'=>90,'longitude'=>180] as $k=>$max){$v=$p[$k]??null;if($v===null||$v===''){$coords[]=null;continue;}
            if(!is_numeric($v)||abs((float)$v)>$max)throw new DomainException('Revisa las coordenadas.');$coords[]=round((float)$v,6);}
        if(($coords[0]===null)!==($coords[1]===null))throw new DomainException('Escribe latitud y longitud, o ninguna.');
        $values=[$name,$address,$hours,$phone,$coords[0],$coords[1],empty($p['active'])?0:1];
        if($id){$q=$this->db->prepare('UPDATE parks SET name=?,address=?,hours=?,phone=?,latitude=?,longitude=?,active=?,updated_at=NOW() WHERE id=?');$q->execute([...$values,$id]);if(!$q->rowCount()&&!$this->exists('parks',$id))throw new DomainException('Parque no encontrado.',404);}
        else{$this->db->prepare('INSERT INTO parks(name,address,hours,phone,latitude,longitude,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?,NOW(),NOW())')->execute($values);$id=(int)$this->db->lastInsertId();}
        Core::audit($this->db,'staff',$staff['id'],'PARK_SAVED','park',$id);return ['id'=>$id];
    }
    private function exists(string $table,int $id): bool { $q=$this->db->prepare("SELECT 1 FROM $table WHERE id=?");$q->execute([$id]);return (bool)$q->fetchColumn(); }
    private function parkId(mixed $value): ?int
    {
        $id=(int)$value;if(!$id)return null;
        if(!$this->exists('parks',$id))throw new DomainException('Selecciona un parque válido.');return $id;
    }
    private static function offer(array $r): array
    {
        return ['id'=>(int)$r['id'],'title'=>$r['title'],'description'=>$r['description'],'park_id'=>$r['park_id']===null?null:(int)$r['park_id'],'park_name'=>$r['park_name'],
            'starts_on'=>$r['starts_on'],'ends_on'=>$r['ends_on'],'coupon_enabled'=>(bool)$r['coupon_enabled'],'coupon_label'=>$r['coupon_label'],'coupon_terms'=>$r['coupon_terms'],'active'=>(bool)$r['active']];
    }
    /** Ofertas vigentes hoy, con el cupón que ya obtuvo la cuenta (si lo obtuvo). */
    public function currentOffers(int $account): array
    {
        $today=$this->now->format('Y-m-d');
        $q=$this->db->prepare('SELECT o.*,p.name park_name,c.code claimed_code FROM offers o LEFT JOIN parks p ON p.id=o.park_id LEFT JOIN coupons c ON c.offer_id=o.id AND c.account_id=? WHERE o.active=1 AND o.starts_on<=? AND o.ends_on>=? ORDER BY o.ends_on,o.id LIMIT 50');
        $q->execute([$account,$today,$today]);
        return array_map(fn($r)=>self::offer($r)+['claimed_code'=>$r['claimed_code']],$q->fetchAll());
    }
    public function allOffers(): array
    {
        return array_map([self::class,'offer'],$this->db->query('SELECT o.*,p.name park_name FROM offers o LEFT JOIN parks p ON p.id=o.park_id ORDER BY o.ends_on DESC,o.id DESC LIMIT 200')->fetchAll());
    }
    public function saveOffer(array $staff,array $p): array
    {
        $id=(int)($p['id']??0);$title=Core::text($p['title']??'',140,'título');$description=Core::longText($p['description']??'',4000,'descripción');
        $park=$this->parkId($p['park_id']??null);$from=Core::date($p['starts_on']??'','de inicio');$until=Core::date($p['ends_on']??'','de fin');
        if($until<$from)throw new DomainException('La fecha de fin debe ser igual o posterior a la de inicio.');
        $coupon=!empty($p['coupon_enabled']);$label=$coupon?Core::text($p['coupon_label']??'',250,'regalo del cupón'):null;$terms=$coupon?Core::longText($p['coupon_terms']??'',2000,'condiciones',false):null;
        $values=[$title,$description,$park,$from,$until,$coupon?1:0,$label,$terms,empty($p['active'])?0:1];
        // Los cupones ya emitidos conservan su regalo y vigencia: editar la oferta no los cambia.
        if($id){if(!$this->exists('offers',$id))throw new DomainException('Oferta no encontrada.',404);$this->db->prepare('UPDATE offers SET title=?,description=?,park_id=?,starts_on=?,ends_on=?,coupon_enabled=?,coupon_label=?,coupon_terms=?,active=?,updated_at=NOW() WHERE id=?')->execute([...$values,$id]);}
        else{$this->db->prepare('INSERT INTO offers(title,description,park_id,starts_on,ends_on,coupon_enabled,coupon_label,coupon_terms,active,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,NOW(),NOW())')->execute([...$values,$staff['id']]);$id=(int)$this->db->lastInsertId();}
        Core::audit($this->db,'staff',$staff['id'],'OFFER_SAVED','offer',$id);return ['id'=>$id];
    }
    private static function event(array $r): array
    {
        return ['id'=>(int)$r['id'],'title'=>$r['title'],'description'=>$r['description'],'park_id'=>$r['park_id']===null?null:(int)$r['park_id'],'park_name'=>$r['park_name'],
            'starts_at'=>$r['starts_at'],'ends_at'=>$r['ends_at'],'active'=>(bool)$r['active']];
    }
    /** Eventos que no han terminado, los más próximos primero. */
    public function upcomingEvents(): array
    {
        $now=$this->now->format('Y-m-d H:i:s');
        $q=$this->db->prepare('SELECT e.*,p.name park_name FROM events e LEFT JOIN parks p ON p.id=e.park_id WHERE e.active=1 AND COALESCE(e.ends_at,e.starts_at)>=? ORDER BY e.starts_at LIMIT 50');$q->execute([$now]);
        return array_map([self::class,'event'],$q->fetchAll());
    }
    public function allEvents(): array
    {
        return array_map([self::class,'event'],$this->db->query('SELECT e.*,p.name park_name FROM events e LEFT JOIN parks p ON p.id=e.park_id ORDER BY e.starts_at DESC LIMIT 200')->fetchAll());
    }
    private static function dateTime(mixed $value,string $label,bool $required=true): ?string
    {
        $v=str_replace('T',' ',trim((string)$value));if($v==='')return $required?throw new DomainException("Revisa la fecha $label."):null;
        if(strlen($v)===16)$v.=':00';$d=DateTimeImmutable::createFromFormat('!Y-m-d H:i:s',$v);
        if(!$d||$d->format('Y-m-d H:i:s')!==$v)throw new DomainException("Revisa la fecha $label.");return $v;
    }
    public function saveEvent(array $staff,array $p): array
    {
        $id=(int)($p['id']??0);$title=Core::text($p['title']??'',140,'título');$description=Core::longText($p['description']??'',4000,'descripción');
        $park=$this->parkId($p['park_id']??null);$from=self::dateTime($p['starts_at']??'','de inicio');$until=self::dateTime($p['ends_at']??'','de fin',false);
        if($until!==null&&$until<$from)throw new DomainException('El fin debe ser posterior al inicio.');
        $values=[$title,$description,$park,$from,$until,empty($p['active'])?0:1];
        if($id){if(!$this->exists('events',$id))throw new DomainException('Evento no encontrado.',404);$this->db->prepare('UPDATE events SET title=?,description=?,park_id=?,starts_at=?,ends_at=?,active=?,updated_at=NOW() WHERE id=?')->execute([...$values,$id]);}
        else{$this->db->prepare('INSERT INTO events(title,description,park_id,starts_at,ends_at,active,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,NOW(),NOW())')->execute([...$values,$staff['id']]);$id=(int)$this->db->lastInsertId();}
        Core::audit($this->db,'staff',$staff['id'],'EVENT_SAVED','event',$id);return ['id'=>$id];
    }
}

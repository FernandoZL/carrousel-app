<?php
declare(strict_types=1);

/**
 * Cuentas de clientes. Se entra con el correo y un código de 6 dígitos enviado ahí; la cuenta se crea al verificar
 * el primer código, así cada cuenta tiene un correo comprobado. La app guarda un token; el servidor solo su HMAC.
 */
final class Customers
{
    public const TOKEN_DAYS=90;
    public const MAX_MEMBERS=8;
    private const INVALID_CODE='El código no es correcto o ya venció. Solicita uno nuevo.';
    public function __construct(private PDO $db,private array $c,private Mailer $mailer) {}
    private function hash(string $token): string { return hash_hmac('sha256','app-token:'.$token,$this->c['app_key']); }
    public function requestCode(mixed $email,string $ip): array
    {
        $email=Core::email($email);
        Core::throttle($this->db,$this->c,'otp-ip:'.$ip,20,900);Core::throttle($this->db,$this->c,'otp-email:'.$email,5,900);
        $q=$this->db->prepare('SELECT active FROM accounts WHERE email=?');$q->execute([$email]);
        if($q->fetchColumn()==='0')throw new DomainException('Esta cuenta está desactivada. Comunícate con Carrousel.',403);
        $challenge=bin2hex(random_bytes(24));$code=(string)random_int(100000,999999);
        Core::transaction($this->db,function()use($email,$challenge,$code){
            $this->db->prepare('UPDATE otp_codes SET consumed_at=NOW() WHERE email=? AND consumed_at IS NULL')->execute([$email]);
            $this->db->prepare('INSERT INTO otp_codes(challenge,email,code_hash,expires_at,created_at) VALUES(?,?,?,DATE_ADD(NOW(),INTERVAL 10 MINUTE),NOW())')->execute([$challenge,$email,password_hash($code,PASSWORD_DEFAULT)]);
        });
        try{$this->mailer->sendCode($email,$code);}
        catch(Throwable $e){
            $this->db->prepare('UPDATE otp_codes SET consumed_at=NOW() WHERE challenge=?')->execute([$challenge]);
            error_log('Code e-mail failed '.get_class($e));throw new DomainException('No pudimos enviar el código. Inténtalo más tarde.',503);
        }
        return ['challenge'=>$challenge,'message'=>'Te enviamos un código a '.$email.'. Vence en 10 minutos.'];
    }
    public function verifyCode(mixed $challenge,mixed $code,mixed $device,string $ip): array
    {
        Core::throttle($this->db,$this->c,'verify-ip:'.$ip,60,900);
        $challenge=(string)$challenge;$code=(string)$code;
        if(!preg_match('/^[a-f0-9]{48}$/',$challenge))throw new DomainException(self::INVALID_CODE,401);
        $device=mb_substr(trim(preg_replace('/[\x00-\x1F\x7F]+/u',' ',(string)$device)??''),0,80);
        $result=Core::transaction($this->db,function()use($challenge,$code,$device){
            $q=$this->db->prepare('SELECT *,expires_at<=NOW() expired FROM otp_codes WHERE challenge=? FOR UPDATE');$q->execute([$challenge]);$o=$q->fetch();
            if(!$o||$o['consumed_at']||$o['expired']||(int)$o['attempts']>=5)return ['error'=>self::INVALID_CODE];
            if(!preg_match('/^\d{6}$/',$code)||!password_verify($code,$o['code_hash'])){
                $this->db->prepare('UPDATE otp_codes SET attempts=attempts+1,consumed_at=IF(attempts>=5,NOW(),NULL) WHERE id=?')->execute([$o['id']]);
                return ['error'=>(int)$o['attempts']+1>=5?'Demasiados intentos con este código. Solicita uno nuevo.':'El código no es correcto.'];
            }
            $this->db->prepare('UPDATE otp_codes SET consumed_at=NOW() WHERE id=?')->execute([$o['id']]);
            $q=$this->db->prepare('INSERT IGNORE INTO accounts(email,created_at,updated_at) VALUES(?,NOW(),NOW())');$q->execute([$o['email']]);$created=$q->rowCount()===1;
            $q=$this->db->prepare('SELECT id,active FROM accounts WHERE email=? FOR UPDATE');$q->execute([$o['email']]);$a=$q->fetch();
            if(!(int)$a['active'])return ['error'=>'Esta cuenta está desactivada. Comunícate con Carrousel.'];
            $token=bin2hex(random_bytes(32));$expires=(new DateTimeImmutable('+'.self::TOKEN_DAYS.' days'))->format('Y-m-d H:i:s');
            $this->db->prepare('INSERT INTO app_tokens(token_hash,account_id,device,created_at,last_used_at,expires_at) VALUES(?,?,?,NOW(),NOW(),?)')->execute([$this->hash($token),$a['id'],$device===''?null:$device,$expires]);
            Core::audit($this->db,'account',(int)$a['id'],$created?'ACCOUNT_CREATED':'LOGIN','account',(int)$a['id'],['device'=>$device]);
            return ['token'=>$token,'expires_at'=>$expires,'new_account'=>$created];
        });
        if(isset($result['error']))throw new DomainException($result['error'],401);
        return $result;
    }
    /** Id de la cuenta de un token vigente. */
    public function authenticate(string $token): int
    {
        if(!preg_match('/^[a-f0-9]{64}$/',$token))throw new DomainException('Inicia sesión para continuar.',401);
        $q=$this->db->prepare('SELECT t.id,t.account_id FROM app_tokens t JOIN accounts a ON a.id=t.account_id WHERE t.token_hash=? AND t.revoked_at IS NULL AND t.expires_at>NOW() AND a.active=1');
        $q->execute([$this->hash($token)]);$row=$q->fetch();if(!$row)throw new DomainException('Tu sesión venció. Inicia sesión de nuevo.',401);
        $this->db->prepare('UPDATE app_tokens SET last_used_at=NOW() WHERE id=? AND last_used_at<DATE_SUB(NOW(),INTERVAL 1 HOUR)')->execute([$row['id']]);
        return (int)$row['account_id'];
    }
    public function logout(string $token): array
    {
        if(preg_match('/^[a-f0-9]{64}$/',$token))$this->db->prepare('UPDATE app_tokens SET revoked_at=NOW() WHERE token_hash=? AND revoked_at IS NULL')->execute([$this->hash($token)]);
        return ['logged_out'=>true];
    }
    public function members(int $account): array
    {
        $q=$this->db->prepare('SELECT id,name,birth_date FROM members WHERE account_id=? ORDER BY birth_date,id');$q->execute([$account]);
        return array_map(fn($m)=>['id'=>(int)$m['id'],'name'=>$m['name'],'birth_date'=>$m['birth_date']],$q->fetchAll());
    }
    public function profile(int $account): array
    {
        $q=$this->db->prepare('SELECT email,name,phone FROM accounts WHERE id=?');$q->execute([$account]);
        return $q->fetch()+['members'=>$this->members($account),'max_members'=>self::MAX_MEMBERS];
    }
    public function updateProfile(int $account,array $p): array
    {
        $name=Core::text($p['name']??'',140,'nombre');$phone=Core::text($p['phone']??'',30,'teléfono',false);
        if($phone!==''&&!preg_match('/^[0-9+() -]{7,30}$/',$phone))throw new DomainException('Revisa el teléfono.');
        $this->db->prepare('UPDATE accounts SET name=?,phone=?,updated_at=NOW() WHERE id=?')->execute([$name,$phone,$account]);
        return $this->profile($account);
    }
    /** Agrega un festejado (el titular o un hijo). La fecha no se edita después: el cupón emitido depende de ella. */
    public function addMember(int $account,array $p,DateTimeImmutable $today): array
    {
        $name=Core::text($p['name']??'',140,'nombre del festejado');$birth=Core::date($p['birth_date']??'','de nacimiento');
        if($birth>$today->format('Y-m-d')||$birth<$today->modify('-110 years')->format('Y-m-d'))throw new DomainException('Revisa la fecha de nacimiento.');
        return Core::transaction($this->db,function()use($account,$name,$birth){
            $this->db->prepare('SELECT id FROM accounts WHERE id=? FOR UPDATE')->execute([$account]);
            $q=$this->db->prepare('SELECT COUNT(*) FROM members WHERE account_id=?');$q->execute([$account]);
            if((int)$q->fetchColumn()>=self::MAX_MEMBERS)throw new DomainException('Llegaste al máximo de '.self::MAX_MEMBERS.' festejados por cuenta.');
            $q=$this->db->prepare('INSERT IGNORE INTO members(account_id,name,birth_date,created_at) VALUES(?,?,?,NOW())');$q->execute([$account,$name,$birth]);
            if($q->rowCount()!==1)throw new DomainException('Ese festejado ya está registrado.');
            Core::audit($this->db,'account',$account,'MEMBER_ADDED','member',(int)$this->db->lastInsertId());
            return $this->members($account);
        });
    }
}

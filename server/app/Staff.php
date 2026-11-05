<?php
declare(strict_types=1);

/**
 * Personal del panel web. admin administra todo; cajero solo consulta y canjea en su parque. Sesión PHP con cookie
 * HttpOnly y token CSRF en cada escritura. Las cuentas se crean con bin/create-staff.php o desde el panel (admin).
 */
final class Staff
{
    public const ROLES=['admin'=>'Administrador','cajero'=>'Cajero'];
    public function __construct(private PDO $db,private array $c) {}
    public static function startSession(): void
    {
        $https=Http::https();
        session_name('CARROUSEL_APP_STAFF');session_save_path(self::sessionDir());
        ini_set('session.use_strict_mode','1');ini_set('session.use_only_cookies','1');
        session_set_cookie_params(['httponly'=>true,'secure'=>$https,'samesite'=>'Strict','path'=>'/']);session_start();
        if(isset($_SESSION['last_seen'])&&time()-$_SESSION['last_seen']>28800){$_SESSION=[];session_regenerate_id(true);}
        $_SESSION['last_seen']=time();$_SESSION['csrf']??=bin2hex(random_bytes(32));
    }
    private static function sessionDir(): string { $d=dirname(__DIR__).'/storage/sessions';if(!is_dir($d))mkdir($d,0700,true);return $d; }
    public static function checkCsrf(): void
    {
        if(!hash_equals((string)($_SESSION['csrf']??''),(string)($_SERVER['HTTP_X_CSRF_TOKEN']??'')))Http::fail('La sesión cambió. Recarga la página.',403);
    }
    public function find(int $id): ?array
    {
        $q=$this->db->prepare('SELECT s.id,s.name,s.email,s.role,s.park_id,s.active,s.auth_version,p.name park_name FROM staff s LEFT JOIN parks p ON p.id=s.park_id WHERE s.id=? AND s.active=1');$q->execute([$id]);$s=$q->fetch();
        if(!$s)return null;$s['id']=(int)$s['id'];$s['park_id']=$s['park_id']===null?null:(int)$s['park_id'];$s['auth_version']=(int)$s['auth_version'];return $s;
    }
    public function current(): ?array
    {
        if(empty($_SESSION['staff_id']))return null;$s=$this->find((int)$_SESSION['staff_id']);
        if(!$s||$s['auth_version']!==($_SESSION['auth_version']??0)){unset($_SESSION['staff_id'],$_SESSION['auth_version']);return null;}return $s;
    }
    public function require(?array $s,string $role='cajero'): array
    {
        if(!$s)throw new DomainException('Inicia sesión para continuar.',401);
        if($role==='admin'&&$s['role']!=='admin')throw new DomainException('Esta acción es solo para administración.',403);
        return $s;
    }
    public function login(mixed $email,mixed $password,string $ip): array
    {
        $email=Core::email($email);Core::throttle($this->db,$this->c,'staff-login-ip:'.$ip,30,900);Core::throttle($this->db,$this->c,'staff-login:'.$email,8,900);
        $q=$this->db->prepare('SELECT id,password_hash FROM staff WHERE email=? AND active=1');$q->execute([$email]);$row=$q->fetch();
        // El mismo mensaje para correo desconocido y contraseña incorrecta; se verifica un hash ficticio para igualar tiempos.
        static $dummy=null;$dummy??=password_hash(random_bytes(16),PASSWORD_DEFAULT);
        if(!password_verify((string)$password,$row['password_hash']??$dummy)||!$row)throw new DomainException('Correo o contraseña incorrectos.',401);
        $s=$this->find((int)$row['id']);session_regenerate_id(true);$_SESSION['staff_id']=$s['id'];$_SESSION['auth_version']=$s['auth_version'];$_SESSION['csrf']=bin2hex(random_bytes(32));
        Core::audit($this->db,'staff',$s['id'],'STAFF_LOGIN','staff',$s['id']);
        return ['staff'=>self::public($s),'csrf'=>$_SESSION['csrf']];
    }
    public static function public(array $s): array { return ['id'=>$s['id'],'name'=>$s['name'],'email'=>$s['email'],'role'=>$s['role'],'park_id'=>$s['park_id'],'park_name'=>$s['park_name']]; }
    public function all(): array
    {
        return array_map(fn($s)=>['id'=>(int)$s['id'],'name'=>$s['name'],'email'=>$s['email'],'role'=>$s['role'],'park_id'=>$s['park_id']===null?null:(int)$s['park_id'],'park_name'=>$s['park_name'],'active'=>(bool)$s['active']],
            $this->db->query('SELECT s.*,p.name park_name FROM staff s LEFT JOIN parks p ON p.id=s.park_id ORDER BY s.name')->fetchAll());
    }
    /** Crea o edita una cuenta. La contraseña es obligatoria al crear; al editar, vacía conserva la anterior. */
    public function save(?array $actor,array $p): array
    {
        $id=(int)($p['id']??0);$name=Core::text($p['name']??'',140,'nombre');$email=Core::email($p['email']??'');$role=(string)($p['role']??'');
        if(!isset(self::ROLES[$role]))throw new DomainException('Selecciona un rol válido.');
        $park=(int)($p['park_id']??0)?:null;if($role==='cajero'&&!$park)throw new DomainException('El cajero necesita un parque.');
        if($park){$q=$this->db->prepare('SELECT 1 FROM parks WHERE id=? AND active=1');$q->execute([$park]);if(!$q->fetchColumn())throw new DomainException('Selecciona un parque activo.');}
        $password=(string)($p['password']??'');if(($password!==''||!$id)&&(strlen($password)<10||strlen($password)>200))throw new DomainException('La contraseña debe tener al menos 10 caracteres.');
        $active=array_key_exists('active',$p)?(empty($p['active'])?0:1):1;
        return Core::transaction($this->db,function()use($actor,$id,$name,$email,$role,$park,$password,$active){
            $admins=array_map('intval',$this->db->query("SELECT id FROM staff WHERE role='admin' AND active=1 FOR UPDATE")->fetchAll(PDO::FETCH_COLUMN));
            if($id&&$admins===[$id]&&($role!=='admin'||!$active))throw new DomainException('Debe quedar al menos un administrador activo.');
            if($id){
                $q=$this->db->prepare('SELECT 1 FROM staff WHERE id=?');$q->execute([$id]);if(!$q->fetchColumn())throw new DomainException('Usuario no encontrado.',404);
                $this->db->prepare('UPDATE staff SET name=?,email=?,role=?,park_id=?,active=?,auth_version=auth_version+1 WHERE id=?')->execute([$name,$email,$role,$park,$active,$id]);
                if($password!=='')$this->db->prepare('UPDATE staff SET password_hash=? WHERE id=?')->execute([password_hash($password,PASSWORD_DEFAULT),$id]);
            }else{
                $this->db->prepare('INSERT INTO staff(name,email,password_hash,role,park_id,active,created_at) VALUES(?,?,?,?,?,?,NOW())')->execute([$name,$email,password_hash($password,PASSWORD_DEFAULT),$role,$park,$active]);$id=(int)$this->db->lastInsertId();
            }
            Core::audit($this->db,'staff',$actor['id']??null,'STAFF_SAVED','staff',$id,['role'=>$role,'park_id'=>$park,'active'=>$active]);
            return ['id'=>$id];
        });
    }
}

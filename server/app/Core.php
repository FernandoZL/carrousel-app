<?php
declare(strict_types=1);

/** Configuración, conexión, transacciones, bitácora y límite de solicitudes. */
final class Core
{
    public static function config(): array
    {
        $file=getenv('CARROUSEL_APP_CONFIG')?:dirname(__DIR__).'/config/local.php';
        if(!is_file($file))throw new RuntimeException('Falta config/local.php. Copia config/local.php.example y ejecuta bin/install.php.');
        $c=require $file;
        if(strlen((string)($c['app_key']??''))<32)throw new RuntimeException('Configura app_key (32 caracteres o más) en config/local.php.');
        return $c;
    }
    public static function connect(array $c,bool $withDatabase=true): PDO
    {
        $dsn='mysql:host='.$c['db_host'].';port='.($c['db_port']??3306).';charset=utf8mb4'.($withDatabase?';dbname='.$c['db_name']:'');
        $db=new PDO($dsn,$c['db_user'],$c['db_pass'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false]);
        $db->exec("SET time_zone='".(new DateTimeImmutable())->format('P')."'");
        return $db;
    }
    public static function transaction(PDO $db,callable $work): mixed
    {
        $db->beginTransaction();
        try{$value=$work();$db->commit();return $value;}
        catch(Throwable $e){if($db->inTransaction())$db->rollBack();throw $e;}
    }
    public static function audit(PDO $db,string $actorType,?int $actorId,string $event,string $entity,int|string|null $id,array $details=[]): void
    {
        $db->prepare('INSERT INTO audit(actor_type,actor_id,event,entity,entity_id,details,created_at) VALUES(?,?,?,?,?,?,NOW())')
            ->execute([$actorType,$actorId,$event,$entity,$id===null?null:(string)$id,json_encode($details,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR)]);
    }
    /** Ventana fija de $seconds; más de $limit solicitudes con la misma clave responde 429. */
    public static function throttle(PDO $db,array $c,string $key,int $limit,int $seconds): void
    {
        $bucket=intdiv(time(),$seconds);$hash=hash_hmac('sha256','limit:'.$key,$c['app_key']);
        $db->prepare('INSERT INTO request_limits(limit_key,bucket,hits) VALUES(?,?,1) ON DUPLICATE KEY UPDATE hits=hits+1')->execute([$hash,$bucket]);
        $q=$db->prepare('SELECT hits FROM request_limits WHERE limit_key=? AND bucket=?');$q->execute([$hash,$bucket]);
        if((int)$q->fetchColumn()>$limit)throw new DomainException('Demasiados intentos. Espera unos minutos e inténtalo de nuevo.',429);
    }
    public static function email(mixed $value): string
    {
        $email=strtolower(trim((string)$value));
        if(!filter_var($email,FILTER_VALIDATE_EMAIL)||strlen($email)>190)throw new DomainException('Escribe un correo válido.');
        return $email;
    }
    /** Texto de una línea, sin caracteres de control, de 1 a $max caracteres (o vacío si $required es false). */
    public static function text(mixed $value,int $max,string $label,bool $required=true): string
    {
        $v=trim(preg_replace('/[\x00-\x1F\x7F]+/u',' ',(string)$value)??'');
        if(($required&&$v==='')||mb_strlen($v)>$max)throw new DomainException("Revisa el campo $label.");
        return $v;
    }
    /** Texto de varias líneas (descripciones y condiciones). */
    public static function longText(mixed $value,int $max,string $label,bool $required=true): string
    {
        $v=trim(str_replace("\r\n","\n",preg_replace('/[\x00-\x09\x0B\x0C\x0E-\x1F\x7F]+/u',' ',(string)$value)??''));
        if(($required&&$v==='')||mb_strlen($v)>$max)throw new DomainException("Revisa el campo $label.");
        return $v;
    }
    public static function date(mixed $value,string $label): string
    {
        $v=(string)$value;$d=DateTimeImmutable::createFromFormat('!Y-m-d',$v);
        if(!$d||$d->format('Y-m-d')!==$v)throw new DomainException("Revisa la fecha $label.");
        return $v;
    }
}

/** Respuestas JSON y lectura de la solicitud. */
final class Http
{
    /** Proxies locales de confianza (cloudflared corre en el mismo servidor y se conecta desde 127.0.0.1). */
    private static array $trusted=['127.0.0.1','::1'];
    public static function start(array $c): void
    {
        self::$trusted=array_values(array_filter((array)($c['trusted_proxies']??self::$trusted),'is_string'));
        header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');header('X-Frame-Options: DENY');header('Referrer-Policy: no-referrer');
        header("Content-Security-Policy: default-src 'none'; frame-ancestors 'none'");
        ini_set('display_errors','0');ini_set('log_errors','1');
        $storage=dirname(__DIR__).'/storage';if(!is_dir($storage))mkdir($storage,0700,true);ini_set('error_log',$storage.'/php-errors.log');
        $host=parse_url('http://'.($_SERVER['HTTP_HOST']??''),PHP_URL_HOST);
        if(!is_string($host)||!in_array(strtolower($host),array_map('strtolower',$c['allowed_hosts']),true))self::fail('Servidor no autorizado.',403);
    }
    public static function success(mixed $data): never {header('Content-Type: application/json; charset=utf-8');echo json_encode(['ok'=>true,'data'=>$data],JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);exit;}
    public static function fail(string $message,int $status=422): never {http_response_code($status);header('Content-Type: application/json; charset=utf-8');echo json_encode(['ok'=>false,'error'=>$message],JSON_UNESCAPED_UNICODE);exit;}
    /** Objeto JSON del cuerpo con valores simples. */
    public static function json(int $max=16384): array
    {
        if(!str_starts_with(strtolower($_SERVER['CONTENT_TYPE']??''),'application/json'))self::fail('Se requiere JSON.',415);
        $raw=file_get_contents('php://input',false,null,0,$max+1);if($raw===false||strlen($raw)>$max)self::fail('Solicitud demasiado grande.',413);
        try{$p=json_decode($raw,true,4,JSON_THROW_ON_ERROR);}catch(JsonException){self::fail('JSON inválido.');}
        if(!is_array($p)||array_is_list($p)&&$p!==[]||array_filter($p,fn($v)=>!is_scalar($v)&&$v!==null))self::fail('Solicitud inválida.');
        return $p;
    }
    public static function query(string $key,int $max=100): string {$v=$_GET[$key]??'';if(!is_string($v)||mb_strlen($v)>$max)self::fail('Parámetro inválido.');return trim($v);}
    /** Acción permitida y método correcto: lecturas por GET, escrituras por POST. */
    public static function action(array $reads,array $writes): string
    {
        $action=self::query('action',40);$method=$_SERVER['REQUEST_METHOD']??'GET';
        if(!in_array($action,[...$reads,...$writes],true))self::fail('Acción no encontrada.',404);
        if(($method!=='GET'&&in_array($action,$reads,true))||($method!=='POST'&&in_array($action,$writes,true)))self::fail('Método no permitido.',405);
        return $action;
    }
    private static function viaProxy(): bool { return in_array((string)($_SERVER['REMOTE_ADDR']??''),self::$trusted,true); }
    /** IP del cliente. Solo se acepta CF-Connecting-IP cuando la conexión viene del proxy de confianza. */
    public static function ip(): string
    {
        $remote=(string)($_SERVER['REMOTE_ADDR']??'');
        $cf=trim((string)($_SERVER['HTTP_CF_CONNECTING_IP']??''));
        return self::viaProxy()&&filter_var($cf,FILTER_VALIDATE_IP)!==false?$cf:$remote;
    }
    /** HTTPS directo, o HTTPS terminado en Cloudflare e informado por el proxy de confianza. */
    public static function https(): bool
    {
        if(!empty($_SERVER['HTTPS'])&&$_SERVER['HTTPS']!=='off')return true;
        return self::viaProxy()&&strtolower((string)($_SERVER['HTTP_X_FORWARDED_PROTO']??''))==='https';
    }
    /** Ejecuta la API y traduce errores sin exponer detalles internos. */
    public static function run(callable $handler): never
    {
        try{$handler();self::fail('Acción no encontrada.',404);}
        catch(DomainException $e){$code=(int)$e->getCode();self::fail($e->getMessage(),$code>=400&&$code<=599?$code:422);}
        catch(PDOException $e){error_log('Database failure '.$e->getCode().' at '.basename($e->getFile()).':'.$e->getLine());self::fail('No se pudo completar la operación.',500);}
        catch(Throwable $e){error_log('Application failure '.get_class($e).' at '.basename($e->getFile()).':'.$e->getLine());self::fail('No se pudo completar la operación. Revisa la instalación.',503);}
    }
}

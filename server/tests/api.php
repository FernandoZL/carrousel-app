<?php
declare(strict_types=1);
// Prueba de punta a punta por HTTP contra carrousel_app_test (se vacía al empezar). Uso: php tests/api.php
$root=dirname(__DIR__);require_once $root.'/app/Core.php';require_once $root.'/app/Staff.php';
$cfg=require $root.'/config/test.php';if($cfg['db_name']!=='carrousel_app_test')throw new RuntimeException('Solo base de pruebas.');
date_default_timezone_set($cfg['timezone']);$db=Core::connect($cfg);
$db->exec('SET FOREIGN_KEY_CHECKS=0');foreach(['coupons','events','offers','app_tokens','otp_codes','members','accounts','staff','parks','request_limits','audit'] as $t)$db->exec("TRUNCATE $t");$db->exec('SET FOREIGN_KEY_CHECKS=1');
$db->exec("UPDATE settings SET setting_value='' WHERE setting_key IN ('birthday_label','birthday_terms')");
$db->exec("INSERT INTO parks(name,address,hours,active,created_at,updated_at) VALUES('Parque Norte','Zona 18','10:00 a 21:00',1,NOW(),NOW()),('Parque Sur','Villa Nueva','',1,NOW(),NOW()),('Oficina','','',0,NOW(),NOW())");
$staff=new Staff($db,$cfg);$pw='clave-de-prueba-123';
$staff->save(null,['name'=>'Admin','email'=>'admin@example.test','role'=>'admin','park_id'=>1,'password'=>$pw]);
$staff->save(null,['name'=>'Cajero Sur','email'=>'cajero@example.test','role'=>'cajero','park_id'=>2,'password'=>$pw]);

$probe=stream_socket_server('tcp://127.0.0.1:0');$port=(int)substr(strrchr(stream_socket_get_name($probe,false),':'),1);fclose($probe);
$null=PHP_OS_FAMILY==='Windows'?'NUL':'/dev/null';
$server=proc_open([PHP_BINARY,'-S',"127.0.0.1:$port",$root.'/tests/router.php'],[1=>['file',$null,'w'],2=>['file',$null,'w']],$pipes,$root);
$n=0;function check(bool $ok,string $text):void{global $n;if(!$ok)throw new RuntimeException('FAIL '.$text);echo "PASS $text\n";$n++;}
function call(int $port,string $file,string $action,?array $body=null,array $headers=[],array $query=[]):array{
    static $cookie='';$h=['Content-Type: application/json',...$headers];if($cookie!==''&&$file==='staff-api.php')$h[]='Cookie: '.$cookie;
    $ctx=stream_context_create(['http'=>['method'=>$body===null?'GET':'POST','header'=>implode("\r\n",$h),'content'=>$body===null?'':json_encode($body),'ignore_errors'=>true]]);
    $raw=file_get_contents("http://127.0.0.1:$port/$file?".http_build_query(['action'=>$action]+$query),false,$ctx);$status=0;
    foreach($http_response_header as $line){if(preg_match('#^HTTP/\S+ (\d{3})#',$line,$m))$status=(int)$m[1];if(preg_match('/^Set-Cookie: (CARROUSEL_APP_STAFF=[^;]+)/i',$line,$m))$cookie=$m[1];}
    return ['status'=>$status,'json'=>json_decode((string)$raw,true)];
}
$app=fn(string $action,?array $body=null,?string $token=null,array $query=[])=>call($port,'api.php',$action,$body,$token?['X-App-Token: '.$token]:[],$query);
$outbox=$root.'/storage/test-outbox.json';
$login=function(string $email)use($app,$outbox):string{
    @unlink($outbox);$r=$app('code-request',['email'=>$email]);if($r['status']!==200)throw new RuntimeException('Código no solicitado: '.json_encode($r));
    preg_match('/es: (\d{6})/',json_decode((string)file_get_contents($outbox),true)['text'],$m);
    $v=$app('code-verify',['challenge'=>$r['json']['data']['challenge'],'code'=>$m[1],'device'=>'Prueba']);return $v['json']['data']['token'];
};
try{
    for($i=0;$i<50&&!@fsockopen('127.0.0.1',$port);$i++)usleep(100000);
    $parks=$app('parks')['json']['data'];
    check(count($parks)===2&&$parks[0]['name']==='Parque Norte','Parques activos sin sesión; los inactivos no aparecen');
    check($app('code-request',['email'=>'no-es-correo'])['status']===422,'Correo inválido rechazado');
    check($app('coupons')['status']===401,'Cupones sin token: 401');

    @unlink($outbox);$r=$app('code-request',['email'=>'Familia@Example.test']);
    check($r['status']===200&&is_file($outbox),'Código enviado al correo');
    $sent=json_decode((string)file_get_contents($outbox),true);preg_match('/es: (\d{6})/',$sent['text'],$m);
    check($sent['to']==='familia@example.test','El correo se normaliza en minúsculas');
    $wrong=$app('code-verify',['challenge'=>$r['json']['data']['challenge'],'code'=>$m[1]==='000000'?'111111':'000000']);
    check($wrong['status']===401,'Código incorrecto rechazado');
    $ok=$app('code-verify',['challenge'=>$r['json']['data']['challenge'],'code'=>$m[1],'device'=>'Pixel']);
    check($ok['status']===200&&$ok['json']['data']['new_account']===true&&strlen($ok['json']['data']['token'])===64,'Código correcto crea la cuenta y entrega token');
    check($app('code-verify',['challenge'=>$r['json']['data']['challenge'],'code'=>$m[1]])['status']===401,'El código no se reutiliza');
    $token=$ok['json']['data']['token'];
    check((int)$db->query("SELECT COUNT(*) FROM app_tokens WHERE token_hash=".$db->quote($token))->fetchColumn()===0,'El token no se guarda en claro');

    $p=$app('profile-save',['name'=>'Familia Pérez','phone'=>'5555-1234'],$token);
    check($p['status']===200&&$p['json']['data']['name']==='Familia Pérez','Perfil guardado');
    $today=new DateTimeImmutable('today');
    $add=$app('member-add',['name'=>'Ana','birth_date'=>$today->modify('-8 years')->format('Y-m-d')],$token);
    check($add['status']===200&&count($add['json']['data'])===1,'Festejado agregado');
    check($app('member-add',['name'=>'Ana','birth_date'=>$today->modify('-8 years')->format('Y-m-d')],$token)['status']===422,'Festejado repetido rechazado');
    check($app('member-add',['name'=>'Futuro','birth_date'=>$today->modify('+1 day')->format('Y-m-d')],$token)['status']===422,'Fecha de nacimiento futura rechazada');
    check($app('coupons',null,$token)['json']['data']===[],'Sin regalo configurado no se emiten cupones');

    // Personal
    $csrf=call($port,'staff-api.php','me')['json']['data']['csrf'];
    check(call($port,'staff-api.php','login',['email'=>'admin@example.test','password'=>'mala'],['X-CSRF-Token: '.$csrf])['status']===401,'Contraseña incorrecta rechazada');
    check(call($port,'staff-api.php','login',['email'=>'admin@example.test','password'=>$pw])['status']===403,'Escritura sin CSRF rechazada');
    $adm=call($port,'staff-api.php','login',['email'=>'admin@example.test','password'=>$pw],['X-CSRF-Token: '.$csrf]);$csrf=$adm['json']['data']['csrf'];
    $staffCall=function(string $a,?array $b=null,array $q=[])use(&$csrf,$port){return call($port,'staff-api.php',$a,$b,['X-CSRF-Token: '.$csrf],$q);};
    check($adm['status']===200&&$adm['json']['data']['staff']['role']==='admin','Administrador inicia sesión');
    $s=$staffCall('settings-save',['birthday_label'=>'Regalo de prueba','birthday_terms'=>'Presenta el código','birthday_days_before'=>15,'birthday_days_after'=>14]);
    check($s['status']===200&&$s['json']['data']['birthday_label']==='Regalo de prueba','Regalo de cumpleaños configurado');

    $list=$app('coupons',null,$token)['json']['data'];
    check(count($list)===1&&$list[0]['kind']==='birthday'&&$list[0]['status']==='available'&&preg_match('/^[A-Z0-9]{6}$/',$list[0]['code'])===1,'Cupón de cumpleaños emitido, vigente y con código de 6 caracteres');
    check($list[0]['valid_from']===$today->modify('-15 days')->format('Y-m-d')&&$list[0]['valid_until']===$today->modify('+14 days')->format('Y-m-d'),'Vigencia de 15 días antes a 14 después');
    check(count($app('coupons',null,$token)['json']['data'])===1,'Consultar de nuevo no duplica el cupón');
    $code=$list[0]['code'];

    $staffCall('settings-save',['birthday_label'=>'Otro regalo','birthday_terms'=>'','birthday_days_before'=>5,'birthday_days_after'=>5]);
    check($app('coupons',null,$token)['json']['data'][0]['label']==='Regalo de prueba','Cambiar el regalo no modifica el cupón emitido');

    $look=$staffCall('lookup',null,['code'=>strtolower($code)]);
    check($look['status']===200&&$look['json']['data']['member_name']==='Ana'&&$look['json']['data']['account_email']==='familia@example.test','Consulta del código muestra festejado y cuenta');

    // Cajero de otro parque canjea; un segundo canje se rechaza.
    $csrf=$staffCall('logout',[])['json']['data']['csrf'];
    $caj=$staffCall('login',['email'=>'cajero@example.test','password'=>$pw]);$csrf=$caj['json']['data']['csrf'];
    check($staffCall('settings')['status']===403,'Cajero no administra el regalo');
    $red=$staffCall('redeem',['code'=>$code]);
    check($red['status']===200&&$red['json']['data']['status']==='redeemed'&&$red['json']['data']['redeemed_park']==='Parque Sur','Canje registra el parque de la cuenta del cajero');
    check($staffCall('redeem',['code'=>$code])['status']===409,'Segundo canje rechazado');
    check($app('coupons',null,$token)['json']['data'][0]['status']==='redeemed','La app muestra el cupón canjeado');

    // Cupón aún no vigente
    $app('member-add',['name'=>'Luis','birth_date'=>$today->modify('+60 days')->modify('-30 years')->format('Y-m-d')],$token);
    $luis=array_values(array_filter($app('coupons',null,$token)['json']['data'],fn($c)=>$c['member_name']==='Luis'))[0];
    check($luis['status']==='upcoming'&&$luis['label']==='Otro regalo','Cumpleaños lejano: cupón aún no disponible con el regalo vigente');
    check($staffCall('redeem',['code'=>$luis['code']])['status']===409,'Cupón aún no vigente no se canjea');

    // Ofertas y eventos
    $csrf=$staffCall('logout',[])['json']['data']['csrf'];$csrf=$staffCall('login',['email'=>'admin@example.test','password'=>$pw])['json']['data']['csrf'];
    $o=$staffCall('offer-save',['title'=>'2x1 en juegos','description'=>'Solo martes','starts_on'=>$today->format('Y-m-d'),'ends_on'=>$today->modify('+7 days')->format('Y-m-d'),'park_id'=>'','coupon_enabled'=>true,'coupon_label'=>'2x1','coupon_terms'=>'','active'=>true]);
    check($o['status']===200,'Oferta con cupón creada');
    $staffCall('offer-save',['title'=>'Pasada','description'=>'x','starts_on'=>'2020-01-01','ends_on'=>'2020-01-02','coupon_enabled'=>false,'active'=>true]);
    $offers=$app('offers',null,$token)['json']['data'];
    check(count($offers)===1&&$offers[0]['claimed_code']===null,'La app muestra solo ofertas vigentes');
    $claim=$app('offer-claim',['offer_id'=>$offers[0]['id']],$token);$again=$app('offer-claim',['offer_id'=>$offers[0]['id']],$token);
    check($claim['status']===200&&$claim['json']['data']['kind']==='offer'&&$again['json']['data']['code']===$claim['json']['data']['code'],'Cupón de oferta: uno por cuenta');
    $e=$staffCall('event-save',['title'=>'Noche de juegos','description'=>'Música y premios','starts_at'=>$today->modify('+3 days')->format('Y-m-d').'T18:00','ends_at'=>'','park_id'=>1,'active'=>true]);
    $events=$app('events')['json']['data'];
    check($e['status']===200&&count($events)===1&&$events[0]['park_name']==='Parque Norte','Evento creado y visible en la app');

    $app('logout',[],$token);
    check($app('coupons',null,$token)['status']===401,'Después de salir el token ya no sirve');
    $other=$login('otra@example.test');
    check(!in_array($code,array_column($app('coupons',null,$other)['json']['data'],'code'),true),'Otra cuenta no ve los cupones ajenos');
    echo "\n$n pruebas correctas.\n";
}finally{proc_terminate($server);}

<?php
declare(strict_types=1);
// Crea una cuenta del personal con una contraseña aleatoria que se muestra una sola vez.
// Uso: php bin/create-staff.php "Nombre" correo@empresa.com admin|cajero [id_parque]
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
$root=dirname(__DIR__);require_once $root.'/app/Core.php';require_once $root.'/app/Staff.php';
try{
    [, $name, $email, $role, $park]=array_pad($argv,5,null);
    if(!$name||!$email||!$role)throw new RuntimeException('Uso: php bin/create-staff.php "Nombre" correo@empresa.com admin|cajero [id_parque]');
    $c=Core::config();date_default_timezone_set($c['timezone']??'America/Guatemala');$db=Core::connect($c);
    $password=rtrim(strtr(base64_encode(random_bytes(12)),'+/','-_'),'=');
    $result=(new Staff($db,$c))->save(null,['name'=>$name,'email'=>$email,'role'=>$role,'park_id'=>$park,'password'=>$password]);
    echo "Cuenta creada (id {$result['id']}).\nContraseña: $password\nGuárdala ahora: no se vuelve a mostrar.\n";
}catch(Throwable $e){fwrite(STDERR,$e->getMessage()."\n");exit(1);}

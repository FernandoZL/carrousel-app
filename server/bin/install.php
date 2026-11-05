<?php
declare(strict_types=1);
// Instala o actualiza el esquema de config/local.php. Repetirlo es seguro: solo agrega lo que falta.
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
$root=dirname(__DIR__);require_once $root.'/app/Core.php';
try{
    $file=getenv('CARROUSEL_APP_CONFIG')?:$root.'/config/local.php';
    if(!is_file($file))throw new RuntimeException('Copia config/local.php.example como config/local.php y complétalo.');
    $source=(string)file_get_contents($file);
    if(str_contains($source,"'app_key'=>'GENERAR'")){
        file_put_contents($file,str_replace("'app_key'=>'GENERAR'","'app_key'=>'".bin2hex(random_bytes(32))."'",$source),LOCK_EX);
        echo "app_key generada en ".basename($file).".\n";
    }
    $c=Core::config();
    $db=Core::connect($c,false);
    $name=$c['db_name'];if(!preg_match('/^[a-z0-9_]{1,64}$/',$name))throw new RuntimeException('db_name inválido.');
    $db->exec("CREATE DATABASE IF NOT EXISTS `$name` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");$db->exec("USE `$name`");
    $db->exec((string)file_get_contents($root.'/database/schema.sql'));
    if(!is_dir($root.'/storage'))mkdir($root.'/storage',0700,true);
    echo "Esquema instalado en $name (versión ".$db->query('SELECT MAX(version) FROM schema_versions')->fetchColumn().").\n";
}catch(Throwable $e){fwrite(STDERR,$e->getMessage()."\n");exit(1);}

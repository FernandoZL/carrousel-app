<?php
declare(strict_types=1);
// Servidor de pruebas (php -S): usa config/test.php y guarda los correos en storage/test-outbox.json. Nunca se publica.
if(!in_array($_SERVER['REMOTE_ADDR']??'',['127.0.0.1','::1'],true)){http_response_code(403);exit;}
putenv('CARROUSEL_APP_CONFIG='.dirname(__DIR__).'/config/test.php');
$GLOBALS['mailTransport']=function(array $m):void{file_put_contents(dirname(__DIR__).'/storage/test-outbox.json',json_encode($m,JSON_UNESCAPED_UNICODE),LOCK_EX);};
$path=parse_url($_SERVER['REQUEST_URI'],PHP_URL_PATH);
if($path==='/api.php'){require dirname(__DIR__).'/public/api.php';return true;}
if($path==='/staff-api.php'){require dirname(__DIR__).'/public/staff-api.php';return true;}
http_response_code(404);return true;

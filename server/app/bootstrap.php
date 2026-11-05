<?php
declare(strict_types=1);
$root=dirname(__DIR__);
if(is_file($root.'/vendor/autoload.php'))require_once $root.'/vendor/autoload.php';
foreach(['Core','Mailer','Customers','Coupons','Content','Staff'] as $class)require_once __DIR__.'/'.$class.'.php';
$config=Core::config();date_default_timezone_set($config['timezone']??'America/Guatemala');
Http::start($config);
$db=Core::connect($config);
// $mailTransport solo lo define el servidor de pruebas (tests/router.php).
$mailer=new Mailer($config,$GLOBALS['mailTransport']??null);
$customers=new Customers($db,$config,$mailer);$coupons=new Coupons($db);$content=new Content($db);

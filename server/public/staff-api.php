<?php
declare(strict_types=1);
// API del panel del personal (admin/). Sesión con cookie y token CSRF en cada escritura.
require_once dirname(__DIR__).'/app/Core.php';
Http::run(function(){
    require dirname(__DIR__).'/app/bootstrap.php';
    $action=Http::action(['me','lookup','parks','offers','events','settings','staff'],['login','logout','redeem','void','park-save','offer-save','event-save','settings-save','staff-save']);
    Staff::startSession();$staffService=new Staff($db,$config);$ip=Http::ip();Core::throttle($db,$config,'staff-api:'.$ip,300,60);
    $p=[];if(($_SERVER['REQUEST_METHOD']??'GET')==='POST'){Staff::checkCsrf();$p=Http::json(32768);}
    if($action==='me'){$s=$staffService->current();Http::success(['csrf'=>$_SESSION['csrf'],'staff'=>$s?Staff::public($s):null,'roles'=>Staff::ROLES]);}
    if($action==='login')Http::success($staffService->login($p['email']??'',$p['password']??'',$ip));
    if($action==='logout'){$_SESSION=['csrf'=>bin2hex(random_bytes(32))];session_regenerate_id(true);Http::success(['csrf'=>$_SESSION['csrf']]);}
    $s=$staffService->require($staffService->current());
    switch($action){
        case 'lookup':Http::success($coupons->lookup(Http::query('code',10)));
        case 'redeem':Http::success($coupons->redeem($s,$p['code']??''));
        case 'parks':Http::success($content->parks(true));
    }
    $s=$staffService->require($s,'admin');
    switch($action){
        case 'void':Http::success($coupons->void($s,$p['code']??'',$p['reason']??''));
        case 'park-save':Http::success($content->savePark($s,$p));
        case 'offers':Http::success($content->allOffers());
        case 'offer-save':Http::success($content->saveOffer($s,$p));
        case 'events':Http::success($content->allEvents());
        case 'event-save':Http::success($content->saveEvent($s,$p));
        case 'settings':Http::success($coupons->settings());
        case 'settings-save':Http::success($coupons->saveSettings($s,$p));
        case 'staff':Http::success($staffService->all());
        case 'staff-save':Http::success($staffService->save($s,$p));
    }
});

<?php
declare(strict_types=1);
// API de la app Android de clientes. Sin cookies: cada solicitud lleva el token de la app en X-App-Token.
require_once dirname(__DIR__).'/app/Core.php';
Http::run(function(){
    require dirname(__DIR__).'/app/bootstrap.php';
    $action=Http::action(['parks','events','profile','coupons','offers'],['code-request','code-verify','logout','profile-save','member-add','offer-claim']);
    $ip=Http::ip();Core::throttle($db,$config,'api:'.$ip,300,60);
    $p=($_SERVER['REQUEST_METHOD']??'GET')==='POST'?Http::json():[];
    $token=(string)($_SERVER['HTTP_X_APP_TOKEN']??'');
    switch($action){
        case 'parks':Http::success($content->parks());
        case 'events':Http::success($content->upcomingEvents());
        case 'code-request':Http::success($customers->requestCode($p['email']??'',$ip));
        case 'code-verify':Http::success($customers->verifyCode($p['challenge']??'',$p['code']??'',$p['device']??'',$ip));
        case 'logout':Http::success($customers->logout($token));
    }
    $account=$customers->authenticate($token);
    switch($action){
        case 'profile':Http::success($customers->profile($account));
        case 'profile-save':Http::success($customers->updateProfile($account,$p));
        case 'member-add':$members=$customers->addMember($account,$p,new DateTimeImmutable('today'));$coupons->issueBirthdays($account);Http::success($members);
        case 'coupons':Http::success($coupons->listForAccount($account));
        case 'offers':Http::success($content->currentOffers($account));
        case 'offer-claim':Http::success($coupons->claimOffer($account,(int)($p['offer_id']??0)));
    }
});

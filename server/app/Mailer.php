<?php
declare(strict_types=1);

use PHPMailer\PHPMailer\PHPMailer;

/**
 * Correo del código de acceso. Con smtp_host lo envía por SMTP (PHPMailer). Sin SMTP y con dev_outbox en true (solo
 * desarrollo local) lo escribe en storage/dev-outbox.json para probar sin correo real; si no, falla.
 */
final class Mailer
{
    public function __construct(private array $c,private ?Closure $transport=null) {}
    public function ready(): bool { return $this->transport!==null||!empty($this->c['smtp_host'])||!empty($this->c['dev_outbox']); }
    public function sendCode(string $email,string $code): void
    {
        $subject='Tu código de acceso | Carrousel';
        $text="Tu código para entrar a la app Carrousel es: $code\nVence en 10 minutos. Si no lo pediste, ignora este mensaje.";
        $html='<div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px;color:#1d2939">'
            .'<h2 style="color:#173b8f;margin:0 0 12px">Tu código de acceso</h2><p>Escríbelo en la app Carrousel para entrar.</p>'
            .'<div style="font-size:36px;font-weight:800;letter-spacing:8px;text-align:center;padding:18px;border:1px solid #d0d5dd;border-radius:12px;background:#f8fafc;color:#173b8f">'.htmlspecialchars($code).'</div>'
            .'<p style="color:#667085;font-size:13px">Vence en 10 minutos. Si no lo pediste, ignora este mensaje.</p></div>';
        $message=['to'=>$email,'subject'=>$subject,'text'=>$text,'html'=>$html];
        if($this->transport){($this->transport)($message);return;}
        if(empty($this->c['smtp_host'])){
            if(empty($this->c['dev_outbox']))throw new RuntimeException('Correo no configurado.');
            file_put_contents(dirname(__DIR__).'/storage/dev-outbox.json',json_encode($message+['sent_at'=>date('c')],JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT),LOCK_EX);return;
        }
        $m=new PHPMailer(true);$m->isSMTP();$m->CharSet='UTF-8';$m->Host=$this->c['smtp_host'];$m->Port=(int)($this->c['smtp_port']??587);
        $m->SMTPSecure=($this->c['smtp_secure']??'tls')==='ssl'?PHPMailer::ENCRYPTION_SMTPS:PHPMailer::ENCRYPTION_STARTTLS;
        if(!empty($this->c['smtp_username'])){$m->SMTPAuth=true;$m->Username=$this->c['smtp_username'];$m->Password=$this->c['smtp_password'];}
        $m->setFrom($this->c['mail_from'],$this->c['mail_from_name']??'Carrousel');$m->addAddress($email);
        $m->Subject=$subject;$m->isHTML(true);$m->Body=$html;$m->AltBody=$text;$m->send();
    }
}

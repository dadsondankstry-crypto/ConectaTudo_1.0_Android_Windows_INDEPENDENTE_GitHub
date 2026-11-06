# ConectaTudo 1.0 — Android + Windows (rede local)

Um único projeto ZIP para o GitHub Actions gerar **um APK para Android** e **um EXE portátil para Windows (Legion Go)**. Os dois conversam pela mesma rede Wi-Fi/hotspot **sem internet**.

**Esta é uma primeira versão funcional de testes.** Não foi compilada em um SDK Android nem executada no Windows real neste ambiente. Compile nos workflows do GitHub e teste antes de depender dela com arquivos importantes.

## O que foi realmente implementado

- **Android:** aplicativo nativo Java, interface em português, serviço de rede com notificação e código PIN, cliente e servidor HTTP, selecionar e enviar vários arquivos, receber em **Downloads/ConectaTudo**, trocar textos, buscar aparelhos por broadcast, IP manual, mostrar e escanear QR, copiar link, abrir painel web, compartilhar tela após autorização **MediaProjection** e visualizar tela de outro dispositivo pelo navegador.
- **Windows 11:** aplicativo Tkinter gerado como **EXE portátil** com PyInstaller, servidor e cliente HTTP local, enviar e receber arquivos, guardar em **Downloads/ConectaTudo**, trocar textos, buscar dispositivos automaticamente por rede local ou conectar por IP, habilitar compartilhamento de tela e abrir tela remota no navegador. Sem necessidade de webcam.
- **Navegador:** painel local acessível em `http://IP:8765/?pin=XXXXXX`, onde é possível enviar arquivos, trocar textos, listar e baixar arquivos recebidos. A aba de tela usa `/screen?pin=XXXXXX` e exige que o dono do dispositivo autorize o compartilhamento.
- Os arquivos são transmitidos em partes (streaming), sem carregar arquivos inteiros na memória do servidor.
- Não utiliza servidor de armazenamento nem envia dados para nuvem. Arquivos **não passam pela internet** quando ambos estão na mesma rede local.

## Como baixar pelo seu Gerenciador GitHub

1. Envie **o ZIP inteiro** para um repositório novo. `android/`, `windows/` e `.github/` devem ficar **diretamente na raiz do repositório**.
2. No GitHub Actions aparecem **dois workflows**: **Compilar Android APK** e **Compilar Windows EXE**. O push dispara ambos. Eles também aceitam execução manual.
3. Baixe o artefato `ConectaTudo-Android-1.0-APK` para encontrar `app-debug.apk`.
4. Baixe `ConectaTudo-Windows-1.0-EXE` para encontrar `ConectaTudo-Windows.exe` (pode vir dentro de um ZIP de artefato do GitHub).
5. Instale o APK no Android. No Windows, descompacte o EXE e abra com duplo clique. **O EXE é portátil; não requer instalador.** O workflow também **tenta gerar** um instalador opcional `ConectaTudo-Setup-1.0.exe` com Inno Setup; caso o Inno Setup esteja indisponível, o EXE portátil continua sendo publicado.
6. Procure também o artefato opcional `ConectaTudo-Windows-1.0-INSTALADOR` se quiser instalar o programa com atalho no menu Iniciar.
7. O Android tem `applicationId` **`br.app.conectatudo.v1`**, independente de seus APKs anteriores.

## Transferência sem internet • POCO ↔ Legion Go

1. Ative o **hotspot do POCO** pelo sistema. Se o HyperOS não permitir hotspot sem dados móveis, conecte ambos a um roteador sem internet ou use um hotspot permitido pelo Windows.
2. Conecte o **Legion Go** ao Wi-Fi do POCO. Não é preciso internet, apenas rede local.
3. Abra ConectaTudo no celular (notificação do serviço ativo) e ConectaTudo no Windows.
4. O Windows pode mostrar um aviso de **Firewall do Windows**: permita a porta/rede privada **somente na sua rede confiável**.
5. Clique **Procurar aparelhos** e, se encontrar o outro, escolha-o. A descoberta pode falhar em redes com isolamento de clientes; nesse caso digite **IP e PIN** exibidos no outro aparelho.
6. Clique **Conectar**, depois **Enviar arquivos** ou **Abrir painel remoto**. O endereço no navegador é do outro aparelho; não precisa de câmera.
7. No Android, os arquivos recebidos são salvos na coleção Downloads, pasta **ConectaTudo**. No Windows, em `Downloads\ConectaTudo`.

## Celular ↔ tablet Android

1. Ambos na mesma rede/hotspot e com o aplicativo aberto.
2. Um mostra o QR em **Mostrar QR**, o outro usa **Ler QR**. Alternativamente, encontre pelo botão **Procurar** ou digite IP+PIN.
3. Conecte, envie arquivos, textos e visualize tela compartilhada. Permita câmera somente para ler QR.

## Compartilhar a tela

- **Windows → Android:** marque **Compartilhar tela deste Windows** no EXE. No Android, informe IP e PIN do Windows e toque **Ver tela do outro dispositivo**. O navegador mostra a imagem com atualização aproximada de 1 segundo. Não é controle remoto nem vídeo de alta taxa de quadros.
- **Android → Windows ou tablet:** no Android emissor toque **Compartilhar minha tela**, aceite o diálogo **nativo do Android**. No Windows ou no outro Android, abra a tela remota. A notificação indica a captura; pare com **Parar compartilhamento**.
- O Android pode apresentar telas protegidas/preto em conteúdos protegidos por DRM; isso não é contornado pelo aplicativo. Ao girar a tela, pode ser necessário reiniciar o compartilhamento para ajustar as dimensões.

## Internet / modo online

**Não implementamos um servidor de sinalização na internet nem relay/WebRTC WAN na versão 1.0.** O modo testado por projeto é rede local, sem internet. Se **ambos** os aparelhos participarem de uma **VPN privada** que permita conexões IP diretas (por exemplo, uma rede mesh VPN configurada separadamente), a conexão manual por IP pode funcionar também a distância, sujeito a roteamento e firewall. Não exponha a porta 8765 diretamente à internet.

## Permissões e segurança

- Android: `INTERNET` (permite rede local, mesmo sem internet externa), serviço em primeiro plano `dataSync`, notificações opcionais para visibilidade, câmera **somente ao escanear QR**, `mediaProjection` somente quando o usuário autoriza, armazenamento por `MediaStore` sem permissões de acesso total a arquivos.
- Windows: a primeira execução pode solicitar permissão para receber conexões no **Firewall do Windows**. Autorize em redes privadas de confiança.
- **IMPORTANTE:** a versão 1.0 usa **HTTP sem TLS** com um PIN de 6 números. Isso **não criptografa arquivos nem PIN** e não protege contra observação da rede. Use apenas Wi-Fi/hotspot de confiança; não abra/encaminhe a porta 8765 na internet. O PIN muda quando o serviço reinicia. Não use para documentos confidenciais em redes públicas.
- Nenhum dos APKs exige Shizuku, acessibilidade, root ou desativar segurança: as permissões necessárias são solicitadas ou explicadas pelo app.
- APKs `debug` compilados em execuções diferentes podem ter assinaturas diferentes; atualizações futuras com o mesmo `applicationId` podem falhar sem chave estável. Não desinstale aplicativos com dados importantes para contornar o problema.

## Estrutura do ZIP

```
.github/workflows/android.yml
.github/workflows/windows.yml
android/settings.gradle
android/build.gradle
android/gradle.properties
android/app/build.gradle
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/br/app/conectatudo/v1/*.java
android/app/src/main/assets/painel.html
android/app/src/main/res/values/styles.xml
windows/app.py
windows/host.py
windows/painel.html
windows/requirements.txt
windows/ConectaTudo.iss
README.md
```

**Observação:** `android/build.gradle` fica dentro de `android/` porque o ZIP combina **dois projetos de plataformas diferentes** em um mesmo repositório. Os workflows apontam explicitamente para a pasta correta. Não há pasta extra envolvendo o projeto dentro do ZIP.

## Como corrigir problema na compilação

- Confira o primeiro erro vermelho real em **Actions → job Android/Windows → etapa que falhou**.
- O Android utiliza **AGP 8.7.3 + Gradle 8.9 + Java 17 + SDK 35**. O workflow instala essas versões e não depende de `gradlew` ausente.
- O Windows utiliza **Python 3.12 + PyInstaller** executado no próprio `windows-latest`, gerando EXE nativo de Windows.

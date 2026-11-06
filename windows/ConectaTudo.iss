#define AppName "ConectaTudo"
#define AppVersion "1.0"
#define AppExe "ConectaTudo-Windows.exe"
[Setup]
AppId={{32F15850-9264-4F86-9C54-9484710D1A50}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=Projeto ConectaTudo
DefaultDirName={localappdata}\Programs\ConectaTudo
DefaultGroupName=ConectaTudo
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=..\dist
OutputBaseFilename=ConectaTudo-Setup-1.0
Compression=lzma
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\{#AppExe}
[Files]
Source: "..\dist\{#AppExe}"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{autoprograms}\ConectaTudo"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\ConectaTudo"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon
[Tasks]
Name: desktopicon; Description: "Criar atalho na área de trabalho"; Flags: unchecked
[Run]
Filename: "{app}\{#AppExe}"; Description: "Abrir ConectaTudo"; Flags: nowait postinstall skipifsilent

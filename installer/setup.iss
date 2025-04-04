; Original app definitions preserved
#define MyAppName "Creative Time And Task Tracking"
#define MyAppVersion "6.7.2"
#define MyAppPublisher "THLHody"
#define MyAppURL ""
#define MyAppExeName "CTTT.url"
#define MyAppId "{{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}}"
#define MyDefaultInstallDir "C:\Program Files\CreativeTimeAndTaskTracker"
#define MyDefaultNetworkPath "\\grafubu\A_Registru graficieni\CTTT"

[Setup]
; Basic setup configuration
AppId={#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={#MyDefaultInstallDir}
DefaultGroupName={#MyAppName}
OutputDir=..\..\target\installer-output
OutputBaseFilename=CTTT_Setup_{#MyAppVersion}
SetupIconFile=graphics\ct3logoicon.ico
Compression=lzma2/ultra64
SolidCompression=yes
PrivilegesRequired=admin
AllowNoIcons=yes
UninstallDisplayIcon={app}\graphics\ct3logoicon.ico
ArchitecturesInstallIn64BitMode=x64compatible
SetupLogging=yes

; Enhanced wizard appearance settings
WizardStyle=modern
WizardSizePercent=120,100
WizardResizable=yes
DisableWelcomePage=no
DisableProgramGroupPage=yes
DisableDirPage=no
AlwaysShowDirOnReadyPage=yes

; Custom colors and styling
WindowVisible=yes
BackColor=$FFFFFF
BackSolid=yes

; Enhanced wizard images
WizardImageFile=graphics\wizard.bmp
WizardImageStretch=no
WizardSmallImageFile=graphics\wizard-small.bmp

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
; Enhanced welcome messages
SetupWindowTitle=Setup - {#MyAppName} v{#MyAppVersion}
WelcomeLabel1=Welcome to {#MyAppName} Setup
WelcomeLabel2=This wizard will guide you through the installation of {#MyAppName} version {#MyAppVersion}.%n%nIt is recommended that you close all other applications before continuing.%n%nThis installation requires administrator privileges.
FinishedLabel=Setup has completed the installation of {#MyAppName} on your computer.%n%nClick Finish to exit Setup.

[CustomMessages]
SetupTypePrompt=Installation Type
SetupTypeDesc=Choose whether to perform a new installation or update an existing installation
InstallLabel=New Installation
UpdateLabel=Update Existing Installation
UpdateNotFound=No existing installation found at the selected location. Please verify the installation directory.
UpdateConfirm=This will update your existing CTTT installation. Continue?
NetworkPathPrompt=Network Configuration
NetworkPathLabel=Network Path:
NetworkPathDesc=Please specify the network path where CTTT data will be stored:
InstallationProgress=Installation Progress
InstallationComplete=Installation Completed Successfully
AppURLs=The application can be accessed at:%n%n    http://localhost:%s%n    http://CTTT:%s

[Code]
{ ----------------------------------------------------------------
  1. Constants and Variables
---------------------------------------------------------------- }
const
  WM_VSCROLL = $0115;
  SB_BOTTOM = 7;
  
  
var
  NetworkPathPage: TInputQueryWizardPage;
  ProgressMemo: TNewMemo;
  AppPort: string;
  LogFile: string;
  ProgressLabel: TNewStaticText;
  ProgressPage: TOutputMsgWizardPage;
  SetupTypePage: TWizardPage;  // Changed from TInputOptionWizardPage
  IsUpdate: Boolean;        
{ ----------------------------------------------------------------
  2. Utility Functions 
---------------------------------------------------------------- }
function IsUpdateSelected(): Boolean;
begin
  Result := IsUpdate;  // Returns the value of our IsUpdate variable
end;

function IsInstallationPresent(const Path: string): Boolean;
begin
  Result := DirExists(Path) and
            FileExists(AddBackslash(Path) + 'ctgraphdep-web.jar') and
            FileExists(AddBackslash(Path) + 'config\application.properties');
end;

procedure SetupTypeRadioClick(Sender: TObject);
begin
  IsUpdate := TNewRadioButton(Sender).Caption = CustomMessage('UpdateLabel');
end;

procedure CreateSetupTypePage;
var
  Page: TWizardPage;
  RadioNew, RadioUpdate: TNewRadioButton;
begin
  // Create the page
  Page := CreateCustomPage(wpWelcome, 
    CustomMessage('SetupTypePrompt'),
    CustomMessage('SetupTypeDesc'));

  // Create radio buttons
  RadioNew := TNewRadioButton.Create(Page);
  with RadioNew do
  begin
    Parent := Page.Surface;
    Caption := CustomMessage('InstallLabel');
    Left := ScaleX(8);
    Top := ScaleY(8);
    Width := Page.SurfaceWidth - ScaleX(16);
    Checked := True;
    OnClick := @SetupTypeRadioClick;
  end;

  RadioUpdate := TNewRadioButton.Create(Page);
  with RadioUpdate do
  begin
    Parent := Page.Surface;
    Caption := CustomMessage('UpdateLabel');
    Left := ScaleX(8);
    Top := RadioNew.Top + RadioNew.Height + ScaleY(4);
    Width := Page.SurfaceWidth - ScaleX(16);
    OnClick := @SetupTypeRadioClick;
  end;

  // Store the page reference
  SetupTypePage := Page;
end;

function ValidateNetworkPath(const Path: string): Boolean;
begin
  Result := True;
  if Length(Trim(Path)) = 0 then
  begin
    Result := False;
    Exit;
  end;
  if Copy(Path, 1, 2) = '\\' then
  begin
    if Length(Path) < 5 then
    begin
      Result := False;
      Exit;
    end;
  end;
end;

procedure WriteLog(const Message: string);
var
  TimeStr: string;
  FullMessage: string;
  LogDir, BackupLogDir: string;
begin
  try
    // Get the correct log directory path
    if ExpandConstant('{app}') <> '' then
      LogDir := ExpandConstant('{app}\logs')
    else
      LogDir := ExpandConstant('{tmp}\logs');
      
    BackupLogDir := ExpandConstant('{tmp}\CTTT_backup_logs');
      
    if LogFile = '' then
      LogFile := LogDir + '\setup_' + GetDateTimeString('yyyymmdd_hhnnss', '_', '') + '.log';
    
    if Length(Message) = 0 then
      Exit;
      
    TimeStr := GetDateTimeString('yyyy-mm-dd hh:nn:ss', #32, #32);
    FullMessage := TimeStr + Message + #13#10;
    
    try
      // Try primary log location
      if not DirExists(LogDir) then
        ForceDirectories(LogDir);
      
      if FileExists(LogFile) then
        SaveStringToFile(LogFile, FullMessage, True)
      else
        SaveStringToFile(LogFile, FullMessage, False);
    except
      // If primary fails, try backup location
      try
        if not DirExists(BackupLogDir) then
          ForceDirectories(BackupLogDir);
          
        LogFile := BackupLogDir + '\setup_backup_' + GetDateTimeString('yyyymmdd_hhnnss', '_', '') + '.log';
        SaveStringToFile(LogFile, FullMessage, True);
      except
        // If both fail, just update the UI
      end;
    end;
    
    // Update progress display with styled text
    if ProgressMemo <> nil then
    begin
      ProgressMemo.Lines.Add(TimeStr + Message);
      SendMessage(ProgressMemo.Handle, WM_VSCROLL, SB_BOTTOM, 0);
    end;
  except
    // Final fallback - silent failure
  end;
end;


function LoadInstallationLog(): TStringList;
var
  LogPath: string;
  LogContents: TStringList;
begin
  LogPath := ExpandConstant('{app}\logs\cttt-setup.log');
  LogContents := TStringList.Create;
  
  try
    if FileExists(LogPath) then
      LogContents.LoadFromFile(LogPath)
    else
      LogContents.Add('Installation log file not found.');
  except
    LogContents.Add('Error reading installation log.');
  end;
  
  Result := LogContents;
end;


procedure UpdateProgressPageWithLog;
var
  LogContents: TStringList;
begin
  if ProgressMemo <> nil then
  begin
    LogContents := LoadInstallationLog;
    try
      ProgressMemo.Lines.Clear;
      ProgressMemo.Lines.AddStrings(LogContents);
      SendMessage(ProgressMemo.Handle, WM_VSCROLL, SB_BOTTOM, 0);
    finally
      LogContents.Free;
    end;
  end;
end;
{ ----------------------------------------------------------------
  3. UI Component Creation
---------------------------------------------------------------- }
procedure CreateEnhancedProgressUI;
begin
  // Create a proper progress page
  ProgressPage := CreateOutputMsgPage(wpInstalling,
    CustomMessage('InstallationProgress'),
    '',  // Description can be empty
    ''); // Status message can be empty
  
  // Create styled progress label
  ProgressLabel := TNewStaticText.Create(WizardForm);
  with ProgressLabel do
  begin
    Parent := ProgressPage.Surface;
    Left := ScaleX(8);
    Top := ScaleY(8);
    Width := ProgressPage.Surface.Width - ScaleX(16);
    Caption := CustomMessage('InstallationProgress');
    Font.Size := 10;
    Font.Style := [fsBold];
    Font.Color := $000000;
    Visible := True;
  end;

  // Create styled progress memo
  ProgressMemo := TNewMemo.Create(WizardForm);
  with ProgressMemo do
  begin
    Parent := ProgressPage.Surface;
    Left := ScaleX(8);
    Top := ProgressLabel.Top + ProgressLabel.Height + ScaleY(8);
    Width := ProgressPage.Surface.Width - ScaleX(16);
    Height := ProgressPage.Surface.Height - ProgressLabel.Height - ScaleY(24);
    ScrollBars := ssVertical;
    ReadOnly := True;
    WantReturns := True;
    Font.Name := 'Segoe UI';
    Font.Size := 9;
    Color := $F8F8F8;
    BorderStyle := bsSingle;
  end;
end;


procedure CreateEnhancedNetworkPathPage;
begin
  NetworkPathPage := CreateInputQueryPage(wpSelectDir,
    CustomMessage('NetworkPathPrompt'),
    CustomMessage('NetworkPathDesc'),
    '');
    
  with NetworkPathPage do
  begin
    Add(CustomMessage('NetworkPathLabel'), False);
    Values[0] := ExpandConstant('{#MyDefaultNetworkPath}');
    
    // Style the input box
    Edits[0].Width := WizardForm.InnerNotebook.Width - ScaleX(32);
    Edits[0].Font.Name := 'Segoe UI';
    Edits[0].Font.Size := 9;
  end;
end;


// Add this procedure to manage progress page visibility
// Modify these procedures for correct page management
procedure ShowProgressPage;
begin
  if ProgressPage <> nil then
  begin
    WizardForm.NextButton.Enabled := False;
    WizardForm.BackButton.Enabled := False;
    ProgressLabel.Caption := CustomMessage('InstallationProgress');
    ProgressMemo.Clear;
    WriteLog('Starting installation...');
  end;
end;

procedure HideProgressPage;
begin
  if ProgressPage <> nil then
  begin
    WizardForm.NextButton.Enabled := True;
    WizardForm.BackButton.Enabled := True;
  end;
end;

// Update CurInstallProgressChanged for proper progress display
procedure CurInstallProgressChanged(CurProgress, MaxProgress: Integer);
begin
  if ProgressPage <> nil then
  begin
    try
      ProgressLabel.Caption := Format(CustomMessage('InstallationProgress') + ' (%d%%)',[(CurProgress * 100) div MaxProgress]);
      WriteLog(Format('Installation progress: %d%%', [(CurProgress * 100) div MaxProgress]));
    except
      // Ignore any errors
    end;
  end;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;  // Don't skip any pages by default
  
  if IsUpdate then
  begin
    if PageID = NetworkPathPage.ID then
      Result := True;
  end;
end;

{ ----------------------------------------------------------------
  4. Business Logic
---------------------------------------------------------------- }
function GetNetworkPath(Param: string): string;
begin
  Result := NetworkPathPage.Values[0];
end;

function VerifyInstallation: Boolean;
begin
  Result := DirExists(ExpandConstant('{app}')) and
            FileExists(ExpandConstant('{app}\ctgraphdep-web.jar')) and
            DirExists(ExpandConstant('{app}\config'));
  if not Result then
    MsgBox('Installation verification failed. Some components are missing.', mbError, MB_OK);
end;


procedure LoadAppPort();
var
  Lines: TArrayOfString;
  I: Integer;
  LineContent: string;
begin
  AppPort := ''; // Initialize to empty
  
  if LoadStringsFromFile(ExpandConstant('{app}\config\application.properties'), Lines) then
  begin
    for I := 0 to GetArrayLength(Lines) - 1 do
    begin
      LineContent := Lines[I];
      if Pos('server.port=', LineContent) = 1 then
      begin
        AppPort := Copy(LineContent, Length('server.port=') + 1, Length(LineContent));
        Break;
      end;
    end;
  end;
end;

{ ----------------------------------------------------------------
  5. Event Handlers
---------------------------------------------------------------- }
procedure InitializeWizard;
begin
  // Style the wizard form
  WizardForm.Color := $FFFFFF;
  WizardForm.Font.Name := 'Segoe UI';
  WizardForm.Font.Size := 9;
  
  CreateSetupTypePage;
  
  // Customize directory page
  with WizardForm.DirEdit do
  begin
    Font.Name := 'Segoe UI';
    Font.Size := 9;
    Width := WizardForm.InnerNotebook.Width - ScaleX(32);
  end;
  
  // Style directory browse button
  with WizardForm.DirBrowseButton do
  begin
    Font.Name := 'Segoe UI';
    Font.Size := 9;
  end;
  
  // Create enhanced UI components
  CreateEnhancedNetworkPathPage;
  CreateEnhancedProgressUI;
  
  // Style buttons
  WizardForm.NextButton.Font.Style := [fsBold];
  WizardForm.CancelButton.Font.Style := [];
end;


procedure CurStepChanged(CurStep: TSetupStep);
begin
  try
    case CurStep of
      ssInstall: 
        begin
          WriteLog('Starting installation process...');
          ShowProgressPage;
          WriteLog('Beginning installation...');
        end;
      ssPostInstall:
        begin
          WriteLog('Installation completed successfully');
          HideProgressPage;
          UpdateProgressPageWithLog;  // Add this line
          ProgressLabel.Caption := CustomMessage('InstallationComplete');
        end;
    end;
  except
    WriteLog('Error in CurStepChanged: ' + GetExceptionMessage);
  end;
end;


// Enhanced finished page
procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpInstalling then
  begin
    UpdateProgressPageWithLog;
  end
  else if CurPageID = wpFinished then
  begin
    LoadAppPort();
    with WizardForm.FinishedLabel do
    begin
      Caption := Format(CustomMessage('AppURLs'), [AppPort, AppPort]);
      Font.Style := [fsBold];
    end;
  end;
end;


// Enhanced NextButtonClick with network path validation
function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  
  if CurPageID = SetupTypePage.ID then
  begin
    if IsUpdate then
    begin
      if not IsInstallationPresent(WizardDirValue) then
      begin
        MsgBox(CustomMessage('UpdateNotFound'), mbError, MB_OK);
        Result := False;
        Exit;
      end;
      
      if MsgBox(CustomMessage('UpdateConfirm'), mbConfirmation, MB_YESNO) = IDNO then
      begin
        Result := False;
        Exit;
      end;
    end;
  end
  else if CurPageID = wpSelectDir then
  begin
    if not DirExists(ExtractFilePath(WizardForm.DirEdit.Text)) then
    begin
      MsgBox('The selected parent directory does not exist. Please select a valid installation path.', 
             mbError, MB_OK);
      Result := False;
      Exit;
    end;
    
    if Length(WizardForm.DirEdit.Text) > 100 then
    begin
      MsgBox('The selected path is too long. Please choose a shorter installation path.', 
             mbError, MB_OK);
      Result := False;
      Exit;
    end;
  end
  else if CurPageID = NetworkPathPage.ID then
  begin
    if not ValidateNetworkPath(NetworkPathPage.Values[0]) then
    begin
      MsgBox('Please enter a valid network path.', mbError, MB_OK);
      Result := False;
      Exit;
    end;
  end;
end;
{ ----------------------------------------------------------------
  6. Installation logic
---------------------------------------------------------------- }

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  try
    WriteLog('Preparing installation directory...');
    if not DirExists(ExpandConstant('{app}')) then
    begin
      if not ForceDirectories(ExpandConstant('{app}')) then
      begin
        Result := 'Unable to create installation directory. Please check permissions.';
        WriteLog('Error: ' + Result);
      end;
    end;

    WriteLog('Creating hosts directory...');
    if not DirExists(ExpandConstant('{app}\hosts')) then
    begin
      if not ForceDirectories(ExpandConstant('{app}\hosts')) then
      begin
        Result := 'Unable to create hosts directory. Please check permissions.';
        WriteLog('Error: ' + Result);
      end;
    end;
  except
    Result := GetExceptionMessage;
    WriteLog('Error during preparation: ' + Result);
  end;
end;


[Files]
; Main application files
Source: "dist\bin\ctgraphdep-web.jar"; DestDir: "{app}"; Flags: ignoreversion; Check: not IsUpdateSelected
Source: "dist\bin\ctgraphdep-web.jar"; DestDir: "{app}\update"; Flags: ignoreversion; Check: IsUpdateSelected
Source: "dist\config\*"; DestDir: "{app}\config"; Flags: ignoreversion recursesubdirs createallsubdirs; Check: not IsUpdateSelected
Source: "dist\config\*"; DestDir: "{app}\update\config"; Flags: ignoreversion recursesubdirs createallsubdirs; Check: IsUpdateSelected
Source: "graphics\ct3logoicon.ico"; DestDir: "{app}\graphics"; Flags: ignoreversion

; Scripts
Source: "dist\scripts\*"; DestDir: "{app}\scripts"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "dist\scripts\start-app.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "dist\scripts\uninstall.ps1"; DestDir: "{app}"; Flags: ignoreversion

[Dirs]
Name: "{app}"; Permissions: users-modify authusers-modify
Name: "{app}\logs"; Permissions: users-modify authusers-modify
Name: "{app}\config"; Permissions: users-modify authusers-modify
Name: "{app}\scripts"; Permissions: users-modify authusers-modify
Name: "{app}\hosts"; Permissions: users-modify authusers-modify

[Run]
Filename: "powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\scripts\{code:GetSetupScript}"" -InstallDir ""{app}"" -NetworkPath ""{code:GetNetworkPath}"" -Version ""{#MyAppVersion}"""; \
  StatusMsg: "{code:GetSetupStatusMsg}"; \
  Flags: runhidden waituntilterminated runascurrentuser

[Code]
function GetSetupScript(Param: string): string;
begin
  if IsUpdate then
    Result := 'update.ps1'
  else
    Result := 'install-start-app.ps1';
end;

function GetSetupStatusMsg(Param: string): string;
begin
  if IsUpdate then
    Result := 'Updating CTTT...'
  else
    Result := 'Installing CTTT...';
end;

[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\uninstall.ps1"" -InstallDir ""{app}"" -Force"; Flags: waituntilterminated runhidden runascurrentuser; RunOnceId: "RemoveCTTT"

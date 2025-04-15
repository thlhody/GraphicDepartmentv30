; Original app definitions preserved
#define MyAppName "Creative Time And Task Tracking"
#define MyAppVersion "6.7.7"
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
UninstallLogMode=append

; Enhanced wizard appearance settings
WizardStyle=modern
WizardSizePercent=120,100
WizardResizable=yes
DisableWelcomePage=no
DisableProgramGroupPage=yes
DisableDirPage=no
AlwaysShowDirOnReadyPage=yes

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
SetupTypeDesc=Choose the type of installation to perform
NewInstallLabel=New Installation
UpdateLabel=Update Existing Installation
UninstallLabel=Uninstall Application
UpdateNotFound=No existing installation found at the selected location. Please verify the installation directory.
UpdateConfirm=This will update your existing CTTT installation. Continue?
UninstallConfirm=This will completely remove the application from your computer. All configuration files will be removed. Continue?
NetworkPathPrompt=Network Configuration
NetworkPathLabel=Network Path:
NetworkPathDesc=Please specify the network path where CTTT data will be stored:
InstallationProgress=Installation Progress
InstallationComplete=Installation Completed Successfully
UninstallationProgress=Uninstallation Progress
UninstallationComplete=Uninstallation Completed Successfully
AppURLs=The application can be accessed at:%n%n    http://localhost:%s%n    http://CTTT:%s
PreparingUninstall=Preparing to uninstall. Removing installation...

[Code]
{ ----------------------------------------------------------------
  1. Constants and Variables
---------------------------------------------------------------- }
const
  WM_VSCROLL = $0115;
  SB_BOTTOM = 7;
  WM_CLOSE = $0010; 
  
  // Installation types
  INSTALL_TYPE_NEW = 0;
  INSTALL_TYPE_UPDATE = 1;
  INSTALL_TYPE_UNINSTALL = 2;
  
var
  NetworkPathPage: TInputQueryWizardPage;
  ProgressMemo: TNewMemo;
  AppPort: string;
  LogFile: string;
  ProgressLabel: TNewStaticText;
  ProgressPage: TOutputMsgWizardPage;
  SetupTypePage: TWizardPage;
  InstallType: Integer;
  RadioNew, RadioUpdate, RadioUninstall: TNewRadioButton;
  
  UninstallMode: Boolean;
  UninstallSuccessful: Boolean;
  UninstallCompleted: Boolean;
  SkipInstallation: Boolean;
  UpdateScriptRun: Boolean; 
  
{ ----------------------------------------------------------------
  2. Utility Functions 
---------------------------------------------------------------- }
function IsUninstallSelected(): Boolean;
begin
  Result := UninstallMode or (InstallType = INSTALL_TYPE_UNINSTALL);
end;

function IsUpdateSelected(): Boolean;
begin
  Result := (InstallType = INSTALL_TYPE_UPDATE);
end;

function IsNewInstallSelected(): Boolean;
begin
  Result := (InstallType = INSTALL_TYPE_NEW);
end;

function IsInstallationPresent(const Path: string): Boolean;
begin
  Result := DirExists(Path) and FileExists(AddBackslash(Path) + 'ctgraphdep-web.jar');
end;

function InitializeUninstall(): Boolean;
begin
  UninstallMode := True;
  Result := True;
end;

procedure SetupTypeRadioClick(Sender: TObject);
begin
  if TNewRadioButton(Sender) = RadioNew then
    InstallType := INSTALL_TYPE_NEW
  else if TNewRadioButton(Sender) = RadioUpdate then
    InstallType := INSTALL_TYPE_UPDATE
  else if TNewRadioButton(Sender) = RadioUninstall then
    InstallType := INSTALL_TYPE_UNINSTALL;
end;

procedure CreateSetupTypePage;
var
  Page: TWizardPage;
  IsInstallPresent: Boolean;
begin
  // Create the page
  Page := CreateCustomPage(wpWelcome, 
    CustomMessage('SetupTypePrompt'),
    CustomMessage('SetupTypeDesc'));

  // Check if installation is present at default location
  IsInstallPresent := IsInstallationPresent(ExpandConstant('{#MyDefaultInstallDir}'));

  // Create radio buttons
  RadioNew := TNewRadioButton.Create(Page);
  with RadioNew do
  begin
    Parent := Page.Surface;
    Caption := CustomMessage('NewInstallLabel');
    Left := ScaleX(8);
    Top := ScaleY(8);
    Width := Page.SurfaceWidth - ScaleX(16);
    Checked := not IsInstallPresent; // Select New Install by default if no installation present
    Enabled := not IsInstallPresent; // Only enable if installation doesn't exist
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
    Enabled := IsInstallPresent; // Only enable if installation exists
    Checked := IsInstallPresent; // Select Update by default if installation present
    OnClick := @SetupTypeRadioClick;
  end;

  RadioUninstall := TNewRadioButton.Create(Page);
  with RadioUninstall do
  begin
    Parent := Page.Surface;
    Caption := CustomMessage('UninstallLabel');
    Left := ScaleX(8);
    Top := RadioUpdate.Top + RadioUpdate.Height + ScaleY(4);
    Width := Page.SurfaceWidth - ScaleX(16);
    Enabled := IsInstallPresent; // Only enable if installation exists
    OnClick := @SetupTypeRadioClick;
  end;
  
  // Set initial installation type based on detection
  if IsInstallPresent then
    InstallType := INSTALL_TYPE_UPDATE
  else
    InstallType := INSTALL_TYPE_NEW;

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

function CloseButtonClick(): Boolean;
begin
  // Always allow closing during uninstall
  if UninstallMode then
  begin
    Result := True;
    UninstallSuccessful := True;
  end
  else
  begin
    Result := False;
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
procedure ShowProgressPage;
begin
  if ProgressPage <> nil then
  begin
    WizardForm.NextButton.Enabled := False;
    WizardForm.BackButton.Enabled := False;
    if IsUninstallSelected then
      ProgressLabel.Caption := CustomMessage('UninstallationProgress')
    else
      ProgressLabel.Caption := CustomMessage('InstallationProgress');
    ProgressMemo.Clear;
    
    if IsUninstallSelected then
      WriteLog('Starting uninstallation...')
    else
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
      if IsUninstallSelected then
        ProgressLabel.Caption := Format(CustomMessage('UninstallationProgress') + ' (%d%%)',[(CurProgress * 100) div MaxProgress])
      else
        ProgressLabel.Caption := Format(CustomMessage('InstallationProgress') + ' (%d%%)',[(CurProgress * 100) div MaxProgress]);
      
      if IsUninstallSelected then
        WriteLog(Format('Uninstallation progress: %d%%', [(CurProgress * 100) div MaxProgress]))
      else
        WriteLog(Format('Installation progress: %d%%', [(CurProgress * 100) div MaxProgress]));
    except
      // Ignore any errors
    end;
  end;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  
  // Skip network path page for updates and uninstalls
  if ((InstallType = INSTALL_TYPE_UPDATE) or (InstallType = INSTALL_TYPE_UNINSTALL)) and 
     (PageID = NetworkPathPage.ID) then
    Result := True;
    
  // Skip directory page for uninstall
  if (InstallType = INSTALL_TYPE_UNINSTALL) and (PageID = wpSelectDir) then
    Result := True;
  
  // If uninstallation is completed, skip all pages except Finished
  if UninstallCompleted then
  begin
    if PageID <> wpFinished then
      Result := True;
  end;
end;

function ShouldSkipInstallation: Boolean;
begin
  Result := SkipInstallation;
end;

function ShouldUpdate: Boolean;
begin
  Result := IsUpdateSelected and UpdateScriptRun and not ShouldSkipInstallation;
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

function ExecuteUninstallation(): Boolean;
var
  UninstallScript, InstallDir, Params: string;
  ResultCode: Integer;
begin
  Result := False;
  
 if UninstallMode then
    InstallDir := ExpandConstant('{app}')  // Use {app} during standalone uninstall
  else 
    InstallDir := WizardDirValue;  // Use wizard directory during installer uninstall option
  
  UninstallScript := InstallDir + '\scripts\uninstall.ps1';
  if not FileExists(UninstallScript) then
    UninstallScript := InstallDir + '\uninstall.ps1';

  if not FileExists(UninstallScript) then
  begin
    MsgBox('Uninstall script could not be found at:' + #13#10 + 
           UninstallScript, mbError, MB_OK);
    Exit;
  end;

  Params := '-InstallDir "' + InstallDir + '" -Force -Purge';

  if Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'), 
          '-NoProfile -ExecutionPolicy Bypass -File "' + UninstallScript + '" ' + Params, 
          '', SW_SHOW, ewWaitUntilTerminated, ResultCode) then
  begin
    Result := (ResultCode = 0);
    
    if not Result then
    begin
      MsgBox('Uninstall script failed with exit code: ' + IntToStr(ResultCode), mbError, MB_OK);
    end;
  end
  else
  begin
    MsgBox('Failed to execute uninstall script.', mbError, MB_OK);
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
  UninstallMode := False;
  UninstallCompleted := False;
  UninstallSuccessful := False;
  SkipInstallation := False;
  UpdateScriptRun := False;
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

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
  begin
    UninstallMode := True;
    UninstallCompleted := ExecuteUninstallation();
    
    if not UninstallCompleted then
    begin
      Abort;
    end
    else
    begin
      // Explicitly mark as successful
      WizardForm.StatusLabel.Caption := 'Uninstallation completed successfully.';
    end;
  end
  else if CurUninstallStep = usDone then
  begin
    // Ensure the uninstall is marked as complete
    UninstallCompleted := True;
    UninstallMode := True;
    WizardForm.Close;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
 ResultCode: Integer;
begin
  try
    case CurStep of
      ssInstall: 
        begin
          if UninstallCompleted then
          begin
            // Skip actual installation for uninstall mode
            Exit;
          end;
          
          if IsUninstallSelected then
            WriteLog('Starting uninstallation process...')
          else
            WriteLog('Starting installation process...');
            
          ShowProgressPage;
          
          if IsUninstallSelected then
            WriteLog('Beginning uninstallation...')
          else
            WriteLog('Beginning installation...');
        end;
      ssPostInstall:
        begin
          // Close any open PowerShell windows
          Exec('taskkill', '/F /IM powershell.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
          
          if UninstallCompleted then
          begin
            WriteLog('Uninstallation completed successfully');
            HideProgressPage;
            if ProgressLabel <> nil then
              ProgressLabel.Caption := CustomMessage('UninstallationComplete');
            
            // Update the finished page text in advance
            with WizardForm.FinishedLabel do
            begin
              Caption := 'Uninstallation has been completed successfully. The application has been removed from your computer.';
              Font.Style := [fsBold];
            end;
          end
          else
          begin
            WriteLog('Installation completed successfully');
            HideProgressPage;
            UpdateProgressPageWithLog;
            ProgressLabel.Caption := CustomMessage('InstallationComplete');
          end;
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
    if not IsUninstallSelected then
      UpdateProgressPageWithLog;
  end
  else if CurPageID = wpFinished then
  begin
    if IsUninstallSelected then
    begin
      with WizardForm.FinishedLabel do
      begin
        Caption := 'Uninstallation has been completed successfully. The application has been removed from your computer.';
        Font.Style := [fsBold];
      end;
    end
    else
    begin
      LoadAppPort();
      with WizardForm.FinishedLabel do
      begin
        Caption := Format(CustomMessage('AppURLs'), [AppPort, AppPort]);
        Font.Style := [fsBold];
      end;
    end;
  end;
  if CurPageID = wpFinished then
  begin
    if IsUninstallSelected then
    begin
      with WizardForm.FinishedLabel do
      begin
        Caption := 'Uninstallation has been completed successfully. The application has been removed from your computer.';
        Font.Style := [fsBold];
      end;
      
      // Automatically close after a short delay
      WizardForm.NextButton.Enabled := True;
      WizardForm.CancelButton.Enabled := False;
    end;
  end;
end;

// Enhanced NextButtonClick with network path validation and uninstall execution
function NextButtonClick(CurPageID: Integer): Boolean;
var
  ConfirmMessage: string;
begin
  Result := True;
  
  // Special handling for uninstall finish page
  if (CurPageID = wpFinished) and (UninstallMode or UninstallCompleted) then
  begin
    // Allow normal exit on finish page
    Exit;
  end;
  
  if CurPageID = SetupTypePage.ID then
  begin
    if InstallType = INSTALL_TYPE_UPDATE then
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
    end
    else if InstallType = INSTALL_TYPE_UNINSTALL then
    begin
      if not IsInstallationPresent(ExpandConstant('{#MyDefaultInstallDir}')) then
      begin
        MsgBox(CustomMessage('UpdateNotFound'), mbError, MB_OK);
        Result := False;
        Exit;
      end;
      
      // Enhanced uninstall warning message
      ConfirmMessage := 'This will completely remove the application and its configuration ' +
                      'from your computer.' + #13#10 + #13#10 + 
                      'This process is irreversible and all custom settings will be lost.' + #13#10 + #13#10 +
                      'Do you want to continue with the uninstallation?';
      
      if MsgBox(ConfirmMessage, mbConfirmation, MB_YESNO) = IDNO then
      begin
        Result := False;
        Exit;
      end;
      
      // Execute uninstallation when user confirms
      if ProgressPage <> nil then
      begin
        ProgressPage.Caption := CustomMessage('UninstallationProgress');
        ProgressPage.Description := CustomMessage('UninstallationProgress');
      end;
      
      if ProgressLabel <> nil then
        ProgressLabel.Caption := CustomMessage('UninstallationProgress');
      
      if ProgressMemo <> nil then
        ProgressMemo.Clear;
        
      WizardForm.NextButton.Enabled := False;
      WizardForm.BackButton.Enabled := False;
      
      WriteLog('Starting uninstallation...');
      
      // Execute uninstallation
      if ExecuteUninstallation() then
      begin
        WriteLog('Uninstallation completed successfully');
        if ProgressLabel <> nil then
          ProgressLabel.Caption := CustomMessage('UninstallationComplete');
        
        // Set flags to indicate uninstall is done
        UninstallMode := True;
        UninstallCompleted := True;
        UninstallSuccessful := True;
        SkipInstallation := True;
        
        // Re-enable the Next button to continue to finish page
        WizardForm.NextButton.Enabled := True;
        WizardForm.NextButton.Caption := 'Finish';
        WizardForm.BackButton.Enabled := False;
        
        Result := True;  // Continue to next page
      end
      else
      begin
        WriteLog('Uninstallation failed');
        MsgBox('The uninstallation process encountered errors. Please check the logs for details.', 
               mbError, MB_OK);
               
        // Re-enable buttons on failure
        WizardForm.NextButton.Enabled := True;
        WizardForm.BackButton.Enabled := True;
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
var
  ResultCode: Integer;
begin
  Result := '';
  
  // If uninstallation was completed, then no error but don't install
  if UninstallCompleted then
  begin
    SkipInstallation := True;
    Exit;
  end;
  
  // For Update mode, ensure the update directory exists and stop running application
  if IsUpdateSelected and not SkipInstallation then
  begin
    // Show progress in the prepare to install screen
    WizardForm.StatusLabel.Caption := 'Preparing for update...';
    
    // Ensure update directories exist
    if not DirExists(ExpandConstant('{app}\update')) then
    begin
      if not ForceDirectories(ExpandConstant('{app}\update')) then
      begin
        Result := 'Unable to create update directory. Please check permissions.';
        Exit;
      end;
    end;
    
    if not DirExists(ExpandConstant('{app}\update\config')) then
    begin
      if not ForceDirectories(ExpandConstant('{app}\update\config')) then
      begin
        Result := 'Unable to create update config directory. Please check permissions.';
        Exit;
      end;
    end;
    
    // Stop the running application before update
    WizardForm.StatusLabel.Caption := 'Stopping running application...';
    WizardForm.ProgressGauge.Style := npbstMarquee;
    
    WriteLog('Stopping any running CTTT instances before update...');
    
    // Execute a PowerShell command to stop CTTT processes
    if Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'),
       '-NoProfile -ExecutionPolicy Bypass -Command "Get-Process -Name java | Where-Object {$_.CommandLine -like ''*ctgraphdep-web.jar*''} | Stop-Process -Force; Start-Sleep -Seconds 2"',
       '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    begin
      WriteLog('Application stop command executed successfully');
    end
    else
    begin
      WriteLog('Warning: Could not execute application stop command, continuing anyway');
      // Not returning error, as the app might not be running
    end;
  end;
end;

[Files]
; Main application files - only for new install and update
Source: "dist\bin\ctgraphdep-web.jar"; DestDir: "{app}"; Flags: ignoreversion; Check: IsNewInstallSelected and not ShouldSkipInstallation
Source: "dist\bin\ctgraphdep-web.jar"; DestDir: "{app}\update"; Flags: ignoreversion; Check: IsUpdateSelected and not ShouldSkipInstallation

; Configuration files - only for new install
Source: "dist\config\*"; DestDir: "{app}\config"; Flags: ignoreversion recursesubdirs createallsubdirs; Check: IsNewInstallSelected and not ShouldSkipInstallation
Source: "dist\config\*"; DestDir: "{app}\update\config"; Flags: ignoreversion recursesubdirs createallsubdirs; Check: IsUpdateSelected and not ShouldSkipInstallation
Source: "graphics\ct3logoicon.ico"; DestDir: "{app}\graphics"; Flags: ignoreversion; Check: not IsUninstallSelected and not ShouldSkipInstallation

; Scripts - only for new install and update
Source: "dist\scripts\*"; DestDir: "{app}\scripts"; Flags: ignoreversion recursesubdirs createallsubdirs; Check: not IsUninstallSelected and not ShouldSkipInstallation

; Copy essential scripts to root directory for direct access - only for new install and update
Source: "dist\scripts\start-app.ps1"; DestDir: "{app}"; Flags: ignoreversion; Check: not IsUninstallSelected and not ShouldSkipInstallation
Source: "dist\scripts\uninstall.ps1"; DestDir: "{app}"; Flags: ignoreversion; Check: not IsUninstallSelected and not ShouldSkipInstallation
Source: "dist\scripts\uninstall.ps1"; DestDir: "{app}\scripts"; Flags: ignoreversion; Check: not ShouldSkipInstallation
Source: "dist\scripts\install.ps1"; DestDir: "{app}"; Flags: ignoreversion; Check: not IsUninstallSelected and not ShouldSkipInstallation
Source: "dist\scripts\update.ps1"; DestDir: "{app}"; Flags: ignoreversion; Check: not IsUninstallSelected and not ShouldSkipInstallation

[Dirs]
Name: "{app}"; Permissions: users-modify authusers-modify; Check: not IsUninstallSelected and not ShouldSkipInstallation
Name: "{app}\logs"; Permissions: users-modify authusers-modify; Check: not IsUninstallSelected and not ShouldSkipInstallation
Name: "{app}\config"; Permissions: users-modify authusers-modify; Check: not IsUninstallSelected and not ShouldSkipInstallation
Name: "{app}\scripts"; Permissions: users-modify authusers-modify; Check: not IsUninstallSelected and not ShouldSkipInstallation
Name: "{app}\hosts"; Permissions: users-modify authusers-modify; Check: not IsUninstallSelected and not ShouldSkipInstallation

[Run]
; NEW INSTALLATION: Run install.ps1 directly
Filename: "powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\install.ps1"" -InstallDir ""{app}"" -NetworkPath ""{code:GetNetworkPath}"" -Version ""{#MyAppVersion}"""; \
  StatusMsg: "Installing CTTT..."; \
  Flags: runhidden waituntilterminated runascurrentuser; \
  Check: IsNewInstallSelected and not ShouldSkipInstallation;

; UPDATE: Simply run the update.ps1 script
Filename: "powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\update.ps1"" -InstallDir ""{app}"" -Version ""{#MyAppVersion}"""; \
  StatusMsg: "Updating CTTT..."; \
  Flags: runhidden waituntilterminated runascurrentuser; \
  Check: IsUpdateSelected and not ShouldSkipInstallation;
  
; UNINSTALL: Run uninstall.ps1 script directly
Filename: "powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\scripts\uninstall.ps1"" -InstallDir ""{app}"" -Force -Purge"; \
  StatusMsg: "Uninstalling CTTT..."; \
  Flags: runhidden waituntilterminated runascurrentuser; \
  Check: IsUninstallSelected;
  
[UninstallRun]
Filename: "powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\scripts\uninstall.ps1"" -InstallDir ""{app}"" -Force -Purge"; \
  Flags: runhidden waituntilterminated runascurrentuser; \
  RunOnceId: "UninstallScript";
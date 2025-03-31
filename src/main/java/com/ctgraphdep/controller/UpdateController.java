package com.ctgraphdep.controller;

import com.ctgraphdep.model.dto.VersionInfoDTO;
import com.ctgraphdep.service.UpdateService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/update")
public class UpdateController  {

    private final UpdateService updateService;

    @Value("${cttt.version:}")
    private String currentVersion;

    protected UpdateController(UpdateService updateService) {

        this.updateService = updateService;
    }


    @GetMapping
    public String checkForUpdate(Model model) {
        try {
            VersionInfoDTO versionInfoDTO = updateService.checkForUpdates();
            model.addAttribute("versionInfoDTO", versionInfoDTO);
            model.addAttribute("currentVersion", currentVersion);

            return "update";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking for updates: " + e.getMessage(), e);
            model.addAttribute("error", "An error occurred while checking for updates: " + e.getMessage());
            return "update";
        }
    }

    @GetMapping("/download")
    @ResponseBody
    public ResponseEntity<Resource> downloadInstaller() {
        try {
            VersionInfoDTO versionInfoDTO = updateService.checkForUpdates();

            if (!versionInfoDTO.isUpdateAvailable() || versionInfoDTO.getInstallerPath() == null) {
                return ResponseEntity.notFound().build();
            }

            Path installerPath = Paths.get(versionInfoDTO.getInstallerPath());
            File file = installerPath.toFile();

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(file);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error downloading installer: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
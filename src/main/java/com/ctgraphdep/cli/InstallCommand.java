package com.ctgraphdep.cli;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ctgraphdep.installer.SelfInstaller;

@Component
@ConditionalOnProperty(name = "cttt.installer.mode", havingValue = "install")
public class InstallCommand implements CommandLineRunner {

    @Autowired
    private SelfInstaller selfInstaller;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("CTTT Installer Mode");
        System.out.println("==================");

        // Let SelfInstaller handle the actual installation
        selfInstaller.run(args);

        // Exit after installation
        System.exit(0);
    }
}
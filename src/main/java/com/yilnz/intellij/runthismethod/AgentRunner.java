package com.yilnz.intellij.runthismethod;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class AgentRunner {

    public static String agentJar = "awsomeTester-agent.jar";

    private static String getAgentPath() {
        String path = null;
        try {
            path = AgentRunner.class.getClassLoader().getResource(agentJar).getPath().replace("!/" + agentJar, "").replace("file:/", "");
        } catch (Exception e) {
            return path;
        }
        try {
            JarFile jarFile = new JarFile(new File(path));
            JarEntry jarEntry = jarFile.getJarEntry(agentJar);
            InputStream is = jarFile.getInputStream(jarEntry);
            File newFile = new File(System.getProperty("java.io.tmpdir"), agentJar);
            FileOutputStream fos = new FileOutputStream(newFile);
            while (is.available() > 0) {  // write contents of 'is' to 'fos'
                fos.write(is.read());
            }
            is.close();
            fos.close();
            return newFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void run(String pid, String contextHolder, String command) {
        try {
            VirtualMachine virtualMachine = VirtualMachine.attach(pid);
            String path = getAgentPath();
            virtualMachine.loadAgent(path, "init " + contextHolder);
            virtualMachine.detach();
        } catch (AgentInitializationException | IOException | AttachNotSupportedException e) {
            e.printStackTrace();
        } catch (AgentLoadException e){
            if(!(e.getMessage() != null && e.getMessage().trim().equals("0"))){
                e.printStackTrace();
            }
        }

        try {
            VirtualMachine virtualMachine = VirtualMachine.attach(pid);
            String path = getAgentPath();
            virtualMachine.loadAgent(path, "run " + command);
            virtualMachine.detach();
        } catch ( AgentInitializationException | IOException | AttachNotSupportedException e) {
            e.printStackTrace();
        } catch (AgentLoadException e){
            if(!(e.getMessage() != null && e.getMessage().trim().equals("0"))){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println(System.getProperty("java.class.path"));
        AgentRunner.run("11992", "com.yilnz.awesome.tester.conf.DefaultContextHolder", "com.yilnz.awesome.tester.controller.IndexController#test1(java.lang.String)");
    }
}
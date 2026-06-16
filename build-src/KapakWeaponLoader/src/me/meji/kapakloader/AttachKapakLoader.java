package me.meji.kapakloader;

import com.sun.tools.attach.VirtualMachine;

public final class AttachKapakLoader {
    private AttachKapakLoader() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: AttachKapakLoader <pid> <agent-jar> [plugin-jar]");
        }
        String pluginJar = args.length >= 3 ? args[2] : "/workspaces/vps/plugins/KapakWeapon.jar";
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            vm.loadAgent(args[1], pluginJar);
        } finally {
            vm.detach();
        }
    }
}

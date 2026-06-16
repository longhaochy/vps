package me.meji.runtime;

import com.sun.tools.attach.VirtualMachine;

public final class AttachRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: AttachRunner <pid> <agent.jar> <command...>");
        }
        String pid = args[0];
        String agentPath = args[1];
        StringBuilder commands = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) {
                commands.append(' ');
            }
            commands.append(args[i]);
        }
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentPath, commands.toString().replace("||", "\n"));
        } finally {
            vm.detach();
        }
    }
}

package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.utils.CommandLogger;
import cn.nukkit.ddui.CustomForm;
import cn.nukkit.ddui.MessageBox;
import cn.nukkit.ddui.Observable;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.Task;

import java.util.Map;

/**
 * Test command for data-driven UI (ddui).
 * Opens a sample custom form when executed.
 */
public class DDUITestCommand extends VanillaCommand {

    public DDUITestCommand(String name) {
        super(name, "commands.dduitest.description");
        this.setPermission("nukkit.command.dduitest");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{});
        this.commandParameters.put("debug", new CommandParameter[]{
                CommandParameter.newEnum("mode", new String[]{"data", "show", "both", "form"})
        });
        this.enableParamTree();
    }

    @Override
    public int execute(CommandSender sender, String commandLabel, Map.Entry<String, ParamList> result, CommandLogger log) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return 0;
        }

        testDialogForm(sender.asPlayer());
        return 1;
    }

    private void testCustomForm(Player player) {
        Observable<String> title = new Observable<>("Count: 0");
        Observable<Long> counter = new Observable<>(0L);

        CustomForm form = new CustomForm(title);
        form.header("Counter Example")
                .slider("Count", 0, 100, counter)
                .button("+ 1", p -> {
                    // 更新值 - UI会自动实时更新
                    counter.setValue(counter.getValue() + 1);
                    title.setValue("Count: " + counter.getValue());
                })
                .closeButton();
        form.show(player);
    }

    private void testDialogForm(Player player) {
        MessageBox messageBox = new MessageBox("Message Box")
                .body("Message Box body")
                .button1("A", player1 -> player1.sendMessage("You selected: A"))
                .button2("B", player1 -> player1.sendMessage("You selected: B"));
        messageBox.show(player);

        Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new Task() {
            @Override
            public void onRun(int currentTick) {
                messageBox.close(player);
            }
        }, 100);
    }
}
package mindurka.coreplugin;

import arc.util.Log;
import arc.util.io.ReusableByteOutStream;
import arc.util.io.Writes;
import mindurka.api.SpecialSettings;
import mindurka.coreplugin.extern.Header;
import mindurka.util.ModifyWorld;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.consumers.Consume;

import java.io.DataOutputStream;

public class Overrides {
    private Overrides() {}

    static void load() {
        Vars.content.blocks().each(x -> x instanceof OverdriveProjector, x -> x.buildType = () -> ((OverdriveProjector) x).new OverdriveBuild() {
            private boolean mdConsumeRefreshState = true;

            @Override
            public boolean mdNetEnabled() {
                if (NetServer.mdSyncTarget == null) return true;
                if (PlayerDataKt.getHasMindurkaCompat(NetServer.mdSyncTarget)) return true;
                return canConsume();
            }

            @Override
            public void updateConsumption() {
                if (this.block.hasConsumers && (!this.cheating() || SpecialSettings.currentMap().overdriveIgnoresCheat)) {
                    if (!this.enabled) {
                        this.potentialEfficiency = this.efficiency = this.optionalEfficiency = 0.0F;
                        this.shouldConsumePower = false;
                    } else {
                        boolean update = this.shouldConsume() && this.productionValid();
                        float minEfficiency = 1.0F;
                        this.efficiency = this.optionalEfficiency = 1.0F;
                        this.shouldConsumePower = true;

                        for (Consume cons : this.block.nonOptionalConsumers) {
                            float result = cons.efficiency(this);
                            if (cons != this.block.consPower && result <= 1.0E-7F) {
                                this.shouldConsumePower = false;
                            }

                            minEfficiency = Math.min(minEfficiency, result);
                        }

                        for (Consume cons : this.block.optionalConsumers) {
                            this.optionalEfficiency = Math.min(this.optionalEfficiency, cons.efficiency(this));
                        }

                        this.efficiency = minEfficiency;
                        this.optionalEfficiency = Math.min(this.optionalEfficiency, minEfficiency);
                        this.potentialEfficiency = this.efficiency;
                        if (!update) {
                            this.efficiency = this.optionalEfficiency = 0.0F;
                        }

                        this.updateEfficiencyMultiplier();
                        if (update && this.efficiency > 0.0F) {
                            for (Consume cons : this.block.updateConsumers) {
                                cons.update(this);
                            }
                        }

                    }
                } else {
                    this.potentialEfficiency = this.enabled && this.productionValid() ? 1.0F : 0.0F;
                    this.efficiency = this.optionalEfficiency = this.shouldConsume() ? this.potentialEfficiency : 0.0F;
                    this.shouldConsumePower = true;
                    this.updateEfficiencyMultiplier();
                }

                boolean state = canConsume();
                if (state != mdConsumeRefreshState) {
                    mdConsumeRefreshState = state;
                    for (Player player : Groups.player) {
                        if (PlayerDataKt.getHasMindurkaCompat(player)) continue;
                        ModifyWorld.syncBuild(player.con, this);
                    }
                }
            }
        });
    }
}

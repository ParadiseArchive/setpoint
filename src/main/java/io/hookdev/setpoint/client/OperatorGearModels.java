package io.hookdev.setpoint.client;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The hard hat and the bump cap are not boxes round the head like other helmets: the hard hat is a shell with a crown,
 * a ridge and a brim all round, the bump cap a cap with a peak. Their textures (64 x 64) follow these boxes. The rest of
 * the operator gear uses the normal armour model.
 */
public final class OperatorGearModels {
    private OperatorGearModels() {}

    private static @Nullable HumanoidModel<?> hardHat, bumpCap;

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layer, Model original) {
                if (hardHat == null) hardHat = new HumanoidModel<>(LayerDefinition.create(hardHatMesh(), 64, 64).bakeRoot());
                return hardHat;
            }
        }, SetpointContent.OPERATOR_HELMET.get());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layer, Model original) {
                if (bumpCap == null) bumpCap = new HumanoidModel<>(LayerDefinition.create(bumpCapMesh(), 64, 64).bakeRoot());
                return bumpCap;
            }
        }, SetpointContent.BUMP_CAP.get());
    }

    /** An empty humanoid with only a head to fill (the model needs every part, even when it draws none of them). */
    private static MeshDefinition headOnly(CubeListBuilder head) {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        PartDefinition root = mesh.getRoot();
        root.retainPartsAndChildren(Set.of());
        root.addOrReplaceChild("head", head, PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return mesh;
    }

    /** Head pixels: y up is negative, the face looks towards negative z. */
    private static MeshDefinition hardHatMesh() {
        return headOnly(CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -9, -4, 8, 4, 8, new CubeDeformation(0.75f))                  // the shell
                .texOffs(0, 16).addBox(-3, -10, -3, 6, 1, 6, new CubeDeformation(0.5f))                 // its rounded crown
                .texOffs(32, 0).addBox(-1, -11, -4, 2, 1, 8, new CubeDeformation(0.25f, 0.25f, 0.5f))    // the ridge over the top
                .texOffs(0, 32).addBox(-5, -5, -6, 10, 1, 11, new CubeDeformation(0.5f, -0.3f, 0.5f))); // the brim, longest at the front
    }

    private static MeshDefinition bumpCapMesh() {
        return headOnly(CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -9, -4, 8, 4, 8, new CubeDeformation(0.6f))                   // the cap with its hard shell inside
                .texOffs(0, 16).addBox(-3, -10, -3, 6, 1, 6, new CubeDeformation(0.35f))                // its rounded top
                .texOffs(0, 32).addBox(-4, -5, -9, 8, 1, 5, new CubeDeformation(0.2f, -0.3f, 0f)));      // the peak over the eyes
    }
}

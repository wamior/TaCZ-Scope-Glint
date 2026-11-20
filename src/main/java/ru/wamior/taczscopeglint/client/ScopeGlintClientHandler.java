package ru.wamior.taczscopeglint.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import ru.wamior.taczscopeglint.Config;
import ru.wamior.taczscopeglint.particle.ModParticles;

import java.util.Optional;

/**
 * Клиентский обработчик Scope Glint (блик оптики).
 *
 * <p>Блик отображается ТОЛЬКО когда:
 * 1. Наблюдатель (тот кто смотрит) САМ находится в режиме зума/прицеливания (если включен requireObserverZoom).
 *    Без зума (с пустыми руками или от бедра) блик не виден!
 * 2. Снайпер (цель) держит оружие с оптическим прицелом (TaCZ scope=true),
 *    текущая кратность которого выше minZoomThreshold. Коллиматоры не дают блик независимо от зума ADS.
 * 3. Снайпер находится в режиме прицеливания (ADS, progress >= 0.7).
 * 4. Снайпер смотрит в сторону наблюдателя (в пределах конуса maxGlintAngle).</p>
 */
public class ScopeGlintClientHandler {

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return;
        }

        // 1. ПРОВЕРКА НАБЛЮДАТЕЛЯ:
        // Если включена настройка requireObserverZoom, то наблюдатель САМ должен находиться в зуме/прицеле!
        // С пустыми руками, от бедра или без нажатия зума игрок бликов НЕ видит!
        if (Config.REQUIRE_OBSERVER_ZOOM.get()) {
            if (!isObserverInZoom(mc)) {
                return;
            }
        }

        double visRange = Config.GLINT_VISIBLE_RANGE.get();
        double visRangeSq = visRange * visRange;
        double minZoom = Config.MIN_ZOOM_THRESHOLD.get();
        double maxAngle = Config.MAX_GLINT_ANGLE.get();
        double minDot = Math.cos(Math.toRadians(maxAngle));
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        for (Player shooter : level.players()) {
            // Не показываем блик самому себе в режиме от первого лица (чтобы не перекрывать экран внутри прицела).
            // Но в режиме от третьего лица (F5) разрешаем для проверки
            if (shooter == mc.player && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }

            // Игнорируем мертвых, наблюдателей и невидимых
            if (!shooter.isAlive() || shooter.isSpectator() || shooter.isInvisible()) {
                continue;
            }

            // Проверка дистанции от камеры до стрелка
            double distSq = cameraPos.distanceToSqr(shooter.getEyePosition());
            if (distSq > visRangeSq) {
                continue;
            }

            // 2. ОРУЖИЕ В РУКАХ СТРЕЛКА
            // Если у стрелка нет оружия в руках — НИКАКИХ БЛИКОВ!
            ItemStack weaponStack = shooter.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(weaponStack);
            if (iGun == null) {
                weaponStack = shooter.getOffhandItem();
                iGun = IGun.getIGunOrNull(weaponStack);
                if (iGun == null) {
                    continue;
                }
            }

            // 3. ДОЛЖЕН БЫТЬ УСТАНОВЛЕН ОПТИЧЕСКИЙ ПРИЦЕЛ
            ResourceLocation scopeId = iGun.getAttachmentId(weaponStack, AttachmentType.SCOPE);
            boolean builtIn = scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID);
            if (builtIn) {
                scopeId = iGun.getBuiltInAttachmentId(weaponStack, AttachmentType.SCOPE);
            }
            if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
                continue;
            }

            Optional<ClientAttachmentIndex> attachmentOpt = TimelessAPI.getClientAttachmentIndex(scopeId);
            // Зум ADS не определяет тип прицела: у коллиматоров TaCZ он может быть 2x-2.5x.
            // Признак scope отличает оптику от коллиматоров (sight=true, scope=false).
            if (attachmentOpt.isEmpty()) {
                continue;
            }

            // 4. ПРОВЕРКА ВЫБРАННОЙ ЛИНЗЫ И КРАТНОСТИ
            CompoundTag scopeTag = iGun.getAttachmentTag(weaponStack, AttachmentType.SCOPE);
            int mode = builtIn ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
            if (!OpticalModeResolver.canGlint(scopeId, attachmentOpt.get(), scopeTag, mode, minZoom)) {
                continue;
            }

            // 5. СТРЕЛОК ДОЛЖЕН БЫТЬ В РЕЖИМЕ ПРИЦЕЛИВАНИЯ (ADS, progress >= 0.7)
            if (shooter == mc.player) {
                IClientPlayerGunOperator localOp = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
                if (!localOp.isAim() || localOp.getClientAimingProgress(1.0f) < 0.7f) {
                    continue;
                }
            } else {
                IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
                if (!operator.getSynIsAiming() || operator.getSynAimingProgress() < 0.7f) {
                    continue;
                }
            }

            // 6. ВЕКТОР ВЗГЛЯДА И ПОЗИЦИЯ ПРИЦЕЛА
            Vec3 look = shooter.getViewVector(1.0f);
            Vec3 scopePos = shooter.getEyePosition().add(look.scale(0.35));

            // 7. ПРОВЕРКА УГЛА: Снайпер должен целиться в сторону наблюдателя (в пределах конуса maxAngle)
            Vec3 diff = cameraPos.subtract(scopePos);
            if (diff.lengthSqr() < 1e-4) {
                continue;
            }
            Vec3 toObserver = diff.normalize();
            if (look.dot(toObserver) < minDot) {
                // Снайпер смотрит в другую сторону — отражение не попадает в глаза наблюдателю
                continue;
            }

            // 8. ПРОВЕРКА УКРЫТИЙ ПО ВСЕЙ ЛИНИИ ВИДИМОСТИ
            // The eye-to-lens segment also prevents the fixed forward offset from crossing cover.
            if (!GlintVisibility.isVisible(level, shooter.getEyePosition(), scopePos)
                    || !GlintVisibility.isVisible(level, cameraPos, scopePos)) {
                continue;
            }

            // Спавним чистую белую вспышку на объективе прицела
            level.addParticle(
                    ModParticles.SCOPE_GLINT.get(),
                    scopePos.x,
                    scopePos.y,
                    scopePos.z,
                    0, 0, 0
            );
        }
    }

    /**
     * Проверяет, находится ли сам наблюдатель в режиме зума/прицеливания.
     */
    public static boolean isObserverInZoom(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }

        // Прицеливание в TaCZ (ADS)
        IClientPlayerGunOperator localOp = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        if (localOp.isAim() && localOp.getClientAimingProgress(1.0f) >= 0.5f) {
            return true;
        }

        // Подзорная труба в ванилле
        if (mc.player.isUsingItem() && mc.player.getUseItem().is(Items.SPYGLASS)) {
            return true;
        }

        return false;
    }

}

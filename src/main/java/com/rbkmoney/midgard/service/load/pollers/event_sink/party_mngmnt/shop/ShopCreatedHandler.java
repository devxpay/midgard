package com.rbkmoney.midgard.service.load.pollers.event_sink.party_mngmnt.shop;

import com.rbkmoney.damsel.payment_processing.Event;
import com.rbkmoney.damsel.payment_processing.PartyChange;
import com.rbkmoney.damsel.payment_processing.ShopEffectUnit;
import com.rbkmoney.geck.common.util.TBaseUtil;
import com.rbkmoney.geck.common.util.TypeUtil;
import com.rbkmoney.midgard.service.clearing.exception.NotFoundException;
import com.rbkmoney.midgard.service.load.dao.party.iface.PartyDao;
import com.rbkmoney.midgard.service.load.dao.party.iface.ShopDao;
import com.rbkmoney.midgard.service.load.utils.ShopUtil;
import com.rbkmoney.midgard.service.load.pollers.event_sink.party_mngmnt.AbstractClaimChangedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.generated.feed.enums.Blocking;
import org.jooq.generated.feed.enums.Suspension;
import org.jooq.generated.feed.tables.pojos.Party;
import org.jooq.generated.feed.tables.pojos.Shop;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ShopCreatedHandler extends AbstractClaimChangedHandler {

    private final ShopDao shopDao;

    private final PartyDao partyDao;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void handle(PartyChange change, Event event) {
        getClaimStatus(change).getAccepted().getEffects().stream()
                .filter(e -> e.isSetShopEffect() && e.getShopEffect().getEffect().isSetCreated()).forEach(e -> {
            long eventId = event.getId();
            ShopEffectUnit shopEffect = e.getShopEffect();
            com.rbkmoney.damsel.domain.Shop shopCreated = shopEffect.getEffect().getCreated();
            String shopId = shopEffect.getShopId();
            String partyId = event.getSource().getPartyId();
            log.info("Start shop created handling, eventId={}, partyId={}, shopId={}", eventId, partyId, shopId);
            Shop shop = new Shop();
            shop.setEventId(eventId);
            shop.setEventCreatedAt(TypeUtil.stringToLocalDateTime(event.getCreatedAt()));
            Party partySource = partyDao.get(partyId);
            if (partySource == null) {
                // TODO: исправить после того как прольется БД
                log.error("Party not found, partyId='{}'", partyId);
                return;
                //throw new NotFoundException(String.format("Party not found, partyId='%s'", partyId));
            }
            shop.setShopId(shopId);
            shop.setPartyId(partyId);
            shop.setCreatedAt(TypeUtil.stringToLocalDateTime(shopCreated.getCreatedAt()));
            shop.setBlocking(TBaseUtil.unionFieldToEnum(shopCreated.getBlocking(), Blocking.class));
            if (shopCreated.getBlocking().isSetUnblocked()) {
                shop.setBlockingUnblockedReason(shopCreated.getBlocking().getUnblocked().getReason());
                shop.setBlockingUnblockedSince(TypeUtil.stringToLocalDateTime(shopCreated.getBlocking().getUnblocked().getSince()));
            } else if (shopCreated.getBlocking().isSetBlocked()) {
                shop.setBlockingBlockedReason(shopCreated.getBlocking().getBlocked().getReason());
                shop.setBlockingBlockedSince(TypeUtil.stringToLocalDateTime(shopCreated.getBlocking().getBlocked().getSince()));
            }
            shop.setSuspension(TBaseUtil.unionFieldToEnum(shopCreated.getSuspension(), Suspension.class));
            if (shopCreated.getSuspension().isSetActive()) {
                shop.setSuspensionActiveSince(TypeUtil.stringToLocalDateTime(shopCreated.getSuspension().getActive().getSince()));
            } else if (shopCreated.getSuspension().isSetSuspended()) {
                shop.setSuspensionSuspendedSince(TypeUtil.stringToLocalDateTime(shopCreated.getSuspension().getSuspended().getSince()));
            }
            shop.setDetailsName(shopCreated.getDetails().getName());
            shop.setDetailsDescription(shopCreated.getDetails().getDescription());
            if (shopCreated.getLocation().isSetUrl()) {
                shop.setLocationUrl(shopCreated.getLocation().getUrl());
            } else {
                throw new IllegalArgumentException("Illegal shop location " + shopCreated.getLocation());
            }
            shop.setCategoryId(shopCreated.getCategory().getId());
            if (shopCreated.isSetAccount()) {
                ShopUtil.fillShopAccount(shop, shopCreated.getAccount());
            }
            shop.setContractId(shopCreated.getContractId());
            shop.setPayoutToolId(shopCreated.getPayoutToolId());
            if (shopCreated.isSetPayoutSchedule()) {
                shop.setPayoutScheduleId(shopCreated.getPayoutSchedule().getId());
            }
            shopDao.save(shop);
            log.info("Shop has been saved, eventId={}, partyId={}, shopId={}", eventId, partyId, shopId);
        });
    }
}

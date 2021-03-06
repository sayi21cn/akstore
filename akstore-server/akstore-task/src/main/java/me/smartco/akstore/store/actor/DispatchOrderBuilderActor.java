package me.smartco.akstore.store.actor;

import akka.actor.UntypedActor;
import me.smartco.akstore.transaction.service.TransactionService;
import me.smartco.akstore.integration.CompositeService;
import me.smartco.akstore.integration.ServiceFacade;
import me.smartco.akstore.store.service.PartnerService;
import me.smartco.akstore.store.message.BuildDispatchOrder;
import me.smartco.akstore.common.model.OrderStatus;
import me.smartco.akstore.store.mongodb.market.DispatchOrder;
import me.smartco.akstore.store.mongodb.partner.Shop;
import me.smartco.akstore.biz.spring.SpringUtil;
import me.smartco.akstore.transaction.model.OrderEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;

import java.util.Date;
import java.util.List;

/**
 * Created by libin on 14-12-26.
 */
public class DispatchOrderBuilderActor extends UntypedActor {
    private Logger logger= LoggerFactory.getLogger(DispatchOrderBuilderActor.class);
    @Override
    public void onReceive(Object message) throws Exception {
        if(message instanceof BuildDispatchOrder){
            logger.info("building dispatch orders");
            BuildDispatchOrder buildDispatchOrder=(BuildDispatchOrder)message;
            ServiceFacade serviceFacade=SpringUtil.getInstance().getBean(ServiceFacade.class);
            PartnerService partnerManager=serviceFacade.getPartnerService();
            CompositeService compositeService=serviceFacade.getCompositeService();
            TransactionService transactionService=serviceFacade.getTransactionService();
            List<Shop> shopList=partnerManager.getShopRepository().findByDispatchOptions(buildDispatchOrder.getDispatchOptions());

            for(Shop shop:shopList){

                DispatchOrder dispatchOrder=new DispatchOrder(shop);
                Date dateFrom=new Date(0);
                DispatchOrder previousOrder=partnerManager.getDispatchOrderRepository().findOneByShop(shop,new Sort(Sort.Direction.DESC,"createTime"));
                if(null!=previousOrder){
                    dateFrom=previousOrder.getCreateTime();
                }
                logger.info("building dispatch orders for "+shop.getName()+" from "+dateFrom);
                List<OrderEntity> orderList=transactionService.findByShopIdAndStatusAndCreateTimeGreaterThan(shop.getId(), OrderStatus.created, dateFrom);
                for(OrderEntity order:orderList){
                    try {
                        dispatchOrder.merge(compositeService.elementOfOrder(order));
                        order.setStatus(OrderStatus.assigned);
                        transactionService.getOrderDAO().save(order);
                    }catch (Exception e){
                        logger.error("merging order",e);
                    }
                }
                partnerManager.getDispatchOrderRepository().save(dispatchOrder);
            }
        }
    }
}

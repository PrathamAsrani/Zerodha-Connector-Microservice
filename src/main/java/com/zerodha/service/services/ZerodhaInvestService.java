package com.zerodha.service.services;

import com.zerodha.service.model.dtos.OrderRequestDto;
import com.zerodha.service.model.dtos.OrderResponseDto;
import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class ZerodhaInvestService {
    private final KiteUtility kiteUtility;
    private OrderService orderService;
    private Logger logger= LoggerFactory.getLogger(ZerodhaInvestService.class);

    public ZerodhaInvestService(KiteUtility kiteUtility , OrderService orderService) {
        this.kiteUtility = kiteUtility;
        this.orderService=orderService;
    }

    public ResponseEntity<Object> placeOrder(OrderRequestDto orderRequestDto){
        logger.info("Placing order.");
        try{
            KiteConnect kiteSdk = kiteUtility.getKiteSdk();
            logger.info("KiteSdk got");

            OrderParams orderParams = new OrderParams();
            logger.info("OrderParams obj created");

            logger.info("Setting params");
            orderParams.quantity = orderRequestDto.quantity;
            orderParams.orderType = orderRequestDto.orderType.getCode();
            orderParams.tradingsymbol = orderRequestDto.tradingsymbol;
            orderParams.product =  orderRequestDto.product.getCode();
            orderParams.exchange = orderRequestDto.exchange.getCode();
            orderParams.transactionType = orderRequestDto.transactionType.getCode();
            orderParams.validity = orderRequestDto.validity.getCode();
            orderParams.price = orderRequestDto.price;
            orderParams.triggerPrice = orderRequestDto.triggerPrice;
            orderParams.tag = orderRequestDto.tag;
            orderParams.disclosedQuantity=orderRequestDto.disclosedQuantity;
            orderParams.squareoff=orderRequestDto.squareoffValue;
            orderParams.stoploss=orderRequestDto.stoplossValue;
            orderParams.trailingStoploss=orderRequestDto.trailingStoploss;

            logger.info("Params set, Creating order.");
            Order order = kiteSdk.placeOrder(orderParams , orderRequestDto.variety.getCode());
            logger.info("Order Placed!");

            boolean result = orderService.saveOrder(orderRequestDto , order.orderId);

            if(result)
                logger.info("Order saved in DB");
            else
                logger.info("Failed to save in DB");

            return ResponseEntity.ok(new OrderResponseDto(order.orderId));
        }
       catch (IOException ex) {
            logger.error("IOException caught");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error" , ex.getMessage()));
        }
        catch (KiteException ex) {
            logger.error("KiteException caught");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error" , ex.getMessage()));
        }
        catch (Exception ex){
            logger.error("Exception caught");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error" , ex.getMessage()));
        }
    }
}

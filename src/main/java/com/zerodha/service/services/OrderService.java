package com.zerodha.service.services;

import com.zerodha.service.model.OrderEntity;
import com.zerodha.service.model.dtos.OrderRequestDto;
import com.zerodha.service.repository.OrderRepo;
import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrderService {
    private OrderRepo orderRepo;
    private Logger logger= LoggerFactory.getLogger(OrderService.class);
    private final KiteUtility kiteUtility;
    public  OrderService(OrderRepo orderRepo, KiteUtility kiteUtility){
        this.orderRepo=orderRepo;
        this.kiteUtility = kiteUtility;
    }

    public ResponseEntity<Object> getOrders(){
        try{
            KiteConnect kiteSdk = kiteUtility.getKiteSdk();

            // Make sure the access token is already set from the login process
            logger.info("Fetching orders for user...");

            // Get all orders
            return ResponseEntity.ok(kiteSdk.getOrders());
        }
        catch(KiteException ex)
        {
            logger.error("Unexcpected error while fetching the orders" + ex.getMessage());
        }
        catch(Exception ex){
            logger.error("Unexcpected error while fetching the orders" + ex.getMessage());
        }
        return null;
    }

    public boolean saveOrder(OrderRequestDto orderRequestDto , String orderId){
        try{
            OrderEntity entity = new OrderEntity();
            logger.info("OrderEntity obj created , Setting up the properties");
            entity.setOrderId(orderId);
            entity.setTradingsymbol(orderRequestDto.getTradingsymbol());
            entity.setExchange(orderRequestDto.getExchange());
            entity.setTransactionType(orderRequestDto.getTransactionType());
            entity.setOrderType(orderRequestDto.getOrderType());
            entity.setQuantity(orderRequestDto.getQuantity());
            entity.setProduct(orderRequestDto.getProduct());
            entity.setPrice(orderRequestDto.getPrice());
            entity.setTriggerPrice(orderRequestDto.getTriggerPrice());
            entity.setDisclosedQuantity(orderRequestDto.getDisclosedQuantity());
            entity.setValidity(orderRequestDto.getValidity());
            entity.setSquareoff(orderRequestDto.getSquareoffValue());
            entity.setStoploss(orderRequestDto.getStoplossValue());
            entity.setTrailingStoploss(orderRequestDto.getTrailingStoploss());
            entity.setVariety(orderRequestDto.getVariety());
            entity.setTag(orderRequestDto.getTag());
            entity.setPlacedAt(Instant.now());

            logger.info("Properties Set. Saving the entity");
            orderRepo.save(entity);

            logger.info("OrderEntity saved for orderId: {}",orderId);
            return true;
        }
        catch (DataAccessException ex) {
            logger.error("Database error while saving orderId {}: {}", orderId, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid data for orderId {}: {}", orderId, ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.error("Unexpected error while saving orderId {}: {}", orderId, ex.getMessage(), ex);
        }
        return false;
    }
}

package com.zerodha.service.services;

import com.zerodha.service.model.OrderEntity;
import com.zerodha.service.model.dtos.OrderRequestDto;
import com.zerodha.service.repository.OrderRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrderService {
    private OrderRepo orderRepo;
    private Logger logger= LoggerFactory.getLogger(OrderService.class);
    public  OrderService(OrderRepo orderRepo){
        this.orderRepo=orderRepo;
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

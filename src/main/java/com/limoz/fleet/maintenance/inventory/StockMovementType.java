package com.limoz.fleet.maintenance.inventory;

/**
 * IN and RETURN increase stock, OUT decreases it, ADJUSTMENT carries its own sign (stock count corrections).
 */
public enum StockMovementType {
    IN, OUT, ADJUSTMENT, RETURN;

    /** Signed stock delta for a requested quantity. */
    public int signedQuantity(int quantity) {
        int magnitude = Math.abs(quantity);
        return switch (this) {
            case IN, RETURN -> magnitude;
            case OUT -> -magnitude;
            case ADJUSTMENT -> quantity;
        };
    }
}

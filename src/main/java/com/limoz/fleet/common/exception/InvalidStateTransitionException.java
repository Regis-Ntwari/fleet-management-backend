package com.limoz.fleet.common.exception;

public class InvalidStateTransitionException extends BusinessRuleException {

    public InvalidStateTransitionException(String entity, Object from, Object to) {
        super("INVALID_STATE_TRANSITION", entity + " cannot move from " + from + " to " + to);
    }
}

package com.lightrail.model.business;

import com.lightrail.exceptions.*;
import com.lightrail.helpers.LightrailConstants;
import com.lightrail.model.api.Card;
import com.lightrail.model.api.CardSearchResult;
import com.lightrail.model.api.Contact;
import com.lightrail.net.APICore;

import java.io.IOException;
import java.util.*;

public class CustomerAccount {
    private Map<String, Card> cardIdForCurrency = new HashMap<>();

    Contact contactObject;
    //String defaultCurrency;

    public String getId() {
        return contactObject.getContactId();
    }

    public String getEmail() {
        return contactObject.getEmail();
    }

    public String getFirstName() {
        return contactObject.getFirstName();
    }

    public String getLastName() {
        return contactObject.getLastName();
    }

    public Collection<String> getAvailableCurrencies() {
        return cardIdForCurrency.keySet();
    }

    private CustomerAccount(Contact contactObject) {
        this.contactObject = contactObject;
    }

    private CustomerAccount(Contact contactObject, List<Card> cards) {
        this.contactObject = contactObject;
        for (Card card : cards)
            cardIdForCurrency.put(card.getCurrency(), card);
    }

    private String getDefaultCurrency() {
        if (cardIdForCurrency.keySet().size() == 1) {
            return cardIdForCurrency.keySet().iterator().next();
        } else {
            throw new BadParameterException("Need to specify the currency.");
        }
    }

    public CustomerAccount addCurrency(String currency) throws AuthorizationException, CouldNotFindObjectException, IOException {
        return addCurrency(currency, 0);
    }
    public CustomerAccount addCurrency(String currency, int initialValue) throws AuthorizationException, CouldNotFindObjectException, IOException {
        Map<String, Object> cardParams = new HashMap<>();
        cardParams.put(LightrailConstants.Parameters.CONTACT_ID, getId());
        cardParams.put(LightrailConstants.Parameters.CURRENCY, currency);
        cardParams.put(LightrailConstants.Parameters.INITIAL_VALUE, initialValue);
        cardParams.put(LightrailConstants.Parameters.CARD_TYPE, LightrailConstants.Parameters.CARD_TYPE_ACCOUNT_CARD);
        return addCurrency(cardParams);
    }

    public CustomerAccount addCurrency(Map<String, Object> parameters) throws AuthorizationException, CouldNotFindObjectException, IOException {
        LightrailConstants.Parameters.requireParameters(Arrays.asList(
                LightrailConstants.Parameters.CURRENCY
        ), parameters);

        String currency = (String) parameters.get(LightrailConstants.Parameters.CURRENCY);
        String idempotencyKey = (String) parameters.get(LightrailConstants.Parameters.USER_SUPPLIED_ID);

        if (idempotencyKey == null) {
            idempotencyKey = UUID.randomUUID().toString();
            parameters.put(LightrailConstants.Parameters.USER_SUPPLIED_ID, idempotencyKey);
        }
        parameters.put(LightrailConstants.Parameters.CARD_TYPE, LightrailConstants.Parameters.CARD_TYPE_ACCOUNT_CARD);
        Card card = APICore.createCard(parameters);
        cardIdForCurrency.put(currency, card);
        return this;
    }

    public LightrailCharge pendingCharge(int amount) throws InsufficientValueException, AuthorizationException, CouldNotFindObjectException, IOException {
        getCardFor(getDefaultCurrency());
        return pendingCharge(amount, getDefaultCurrency());
    }
    public LightrailCharge charge(int amount) throws InsufficientValueException, AuthorizationException, CouldNotFindObjectException, IOException {
        getCardFor(getDefaultCurrency());
        return charge(amount, getDefaultCurrency());
    }

    public LightrailCharge charge (int amount, boolean capture) throws InsufficientValueException, AuthorizationException, CouldNotFindObjectException, IOException {
        getCardFor(getDefaultCurrency());
        return charge(amount, getDefaultCurrency(), capture);
    }

    public LightrailCharge pendingCharge(int amount, String currency) throws InsufficientValueException, AuthorizationException, CouldNotFindObjectException, IOException {
        return charge( amount,  currency, false);
    }

    public LightrailCharge charge(int amount, String currency) throws InsufficientValueException, AuthorizationException, CouldNotFindObjectException, IOException {
        return charge( amount,  currency, true);
    }

    public LightrailCharge charge(int amount, String currency, boolean capture) throws IOException, AuthorizationException, CouldNotFindObjectException, InsufficientValueException {
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put(LightrailConstants.Parameters.AMOUNT, amount);
        chargeParams.put(LightrailConstants.Parameters.CURRENCY, currency);
        chargeParams.put(LightrailConstants.Parameters.CAPTURE, capture);
        return charge(chargeParams);
    }

    public LightrailCharge charge(Map<String, Object> chargeParams) throws AuthorizationException, CouldNotFindObjectException, InsufficientValueException, IOException {
        LightrailConstants.Parameters.requireParameters(Arrays.asList(
                LightrailConstants.Parameters.AMOUNT,
                LightrailConstants.Parameters.CURRENCY
        ), chargeParams);

        String currency = (String) chargeParams.get(LightrailConstants.Parameters.CURRENCY);
        Card cardObject = cardIdForCurrency.get(currency);
        if (cardObject == null)
            throw new BadParameterException(String.format("Currency %s is not defined for this account. ", currency));
        String cardId = cardObject.getCardId();

        chargeParams.put(LightrailConstants.Parameters.CARD_ID, cardId);

        return LightrailCharge.create(chargeParams);
    }

    public LightrailFund fund(int amount) throws AuthorizationException, CouldNotFindObjectException, IOException {
        getCardFor(getDefaultCurrency());
        return fund( amount, getDefaultCurrency());
    }

    public LightrailFund fund(int amount, String currency) throws AuthorizationException, CouldNotFindObjectException, IOException {
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put(LightrailConstants.Parameters.AMOUNT, amount);
        chargeParams.put(LightrailConstants.Parameters.CURRENCY, currency);
        return fund(chargeParams);
    }

    public LightrailFund fund(Map<String, Object> fundParams) throws AuthorizationException, CouldNotFindObjectException, IOException {
        LightrailConstants.Parameters.requireParameters(Arrays.asList(
                LightrailConstants.Parameters.AMOUNT,
                LightrailConstants.Parameters.CURRENCY
        ), fundParams);

        String currency = (String) fundParams.get(LightrailConstants.Parameters.CURRENCY);
        Card cardObject = getCardFor(currency);
        String cardId = cardObject.getCardId();
        fundParams.put(LightrailConstants.Parameters.CARD_ID, cardId);

        return LightrailFund.create(fundParams);
    }

    public LightrailValue balance () throws AuthorizationException, CurrencyMismatchException, CouldNotFindObjectException, IOException {
        getCardFor(getDefaultCurrency());
        return balance(getDefaultCurrency());
    }

    public LightrailValue balance (String currency) throws AuthorizationException, CurrencyMismatchException, CouldNotFindObjectException, IOException {
        return LightrailValue.retrieveByCardId(getCardFor(currency).getCardId());
    }

    Card getCardFor(String currency) {
        Card cardObject = cardIdForCurrency.get(currency);
        if (cardObject == null)
            throw new BadParameterException(String.format("Currency %s is not defined for this account.", currency));
        else
            return cardObject;
    }

    public static CustomerAccount create(String email, String firstName, String lastName, String defaultCurrency, int initialBalance) throws AuthorizationException, CouldNotFindObjectException, IOException {
        return create(email, firstName, lastName).
                addCurrency(defaultCurrency,initialBalance);
    }
    public static CustomerAccount create(String email, String firstName, String lastName) throws AuthorizationException, CouldNotFindObjectException, IOException {
        if (email == null || email.isEmpty())
            throw new BadParameterException("Need to provide an email address for the new CustomerAccount.");

        Map<String, Object> customerAccountParams = new HashMap<>();
        customerAccountParams.put(LightrailConstants.Parameters.EMAIL, email);
        if (firstName != null)
            customerAccountParams.put(LightrailConstants.Parameters.FIRST_NAME, firstName);
        if (lastName != null)
            customerAccountParams.put(LightrailConstants.Parameters.LAST_NAME, lastName);

        return create(customerAccountParams);
    }

    public static CustomerAccount create(Map<String, Object> customerAccountParams) throws AuthorizationException, CouldNotFindObjectException, IOException {
        LightrailConstants.Parameters.requireParameters(Arrays.asList(LightrailConstants.Parameters.EMAIL), customerAccountParams);

        String idempotencyKey = (String) customerAccountParams.get(LightrailConstants.Parameters.USER_SUPPLIED_ID);

        if (idempotencyKey == null) {
            idempotencyKey = UUID.randomUUID().toString();
            customerAccountParams.put(LightrailConstants.Parameters.USER_SUPPLIED_ID, idempotencyKey);
        }

        Contact contactObject = APICore.createContact(customerAccountParams);
        return new CustomerAccount(contactObject);
    }

    public static CustomerAccount retrieve(String customerAccountId) throws AuthorizationException, CouldNotFindObjectException, IOException {
        Contact contactObject = APICore.retrieveContact(customerAccountId);
        CardSearchResult cards = APICore.retrieveCardsOfContact(customerAccountId);
        CustomerAccount customerAccount = new CustomerAccount(contactObject, cards.getCards());
        return customerAccount;
    }

    private static void cancelCard(String cardId, String idempotencyKey) throws AuthorizationException, CouldNotFindObjectException, IOException {
        Map<String, Object> params = new HashMap<>();
        params.put(LightrailConstants.Parameters.USER_SUPPLIED_ID, idempotencyKey);

        APICore.cancelCard(cardId, params);
    }

//    public static void delete(String customerAccountId) throws AuthorizationException, CouldNotFindObjectException, IOException {
//        CustomerAccount customerAccount = retrieve(customerAccountId);
//        for (String currency : customerAccount.getAvailableCurrencies()) {
//            Card card = customerAccount.getCardFor(currency);
//            String idempotencyKey = card.getUserSuppliedId() + "-cancel";
//            String cardId = card.getCardId();
//            cancelCard(cardId, idempotencyKey);
//        }
//        APICore.deleteContact(customerAccountId);
//    }
}
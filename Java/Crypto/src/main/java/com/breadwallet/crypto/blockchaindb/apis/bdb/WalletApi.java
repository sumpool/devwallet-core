/*
 * Created by Michael Carrara <michael.carrara@breadwallet.com> on 7/1/19.
 * Copyright (c) 2019 Breadwinner AG.  All right reserved.
*
 * See the LICENSE file at the project root for license information.
 * See the CONTRIBUTORS file at the project root for a list of contributors.
 */
package com.breadwallet.crypto.blockchaindb.apis.bdb;

import com.breadwallet.crypto.blockchaindb.errors.QueryError;
import com.breadwallet.crypto.blockchaindb.models.bdb.Wallet;
import com.breadwallet.crypto.utility.CompletionHandler;
import com.google.common.collect.ImmutableMultimap;

public class WalletApi {

    private final BdbApiClient jsonClient;

    public WalletApi(BdbApiClient jsonClient) {
        this.jsonClient = jsonClient;
    }


    public void getOrCreateWallet(Wallet wallet, CompletionHandler<Wallet, QueryError> handler) {
        getWallet(wallet.getId(), new CompletionHandler<Wallet, QueryError>() {
            @Override
            public void handleData(Wallet data) {
                handler.handleData(data);
            }

            @Override
            public void handleError(QueryError error) {
                createWallet(wallet, handler);
            }
        });
    }

    public void createWallet(Wallet wallet, CompletionHandler<Wallet, QueryError> handler) {
        jsonClient.sendPost("wallets", ImmutableMultimap.of(), Wallet.asJson(wallet), Wallet::asWallet, handler);
    }

    public void getWallet(String id, CompletionHandler<Wallet, QueryError> handler) {
        jsonClient.sendGetWithId("wallets", id, ImmutableMultimap.of(), Wallet::asWallet, handler);
    }

    public void updateWallet(Wallet wallet, CompletionHandler<Wallet, QueryError> handler) {
        jsonClient.sendPutWithId("wallets", wallet.getId(), ImmutableMultimap.of(), Wallet.asJson(wallet),
                Wallet::asWallet, handler);
    }

    public void deleteWallet(String id, CompletionHandler<Wallet, QueryError> handler) {
        jsonClient.sendDeleteWithId("wallets", id, ImmutableMultimap.of(), Wallet::asWallet, handler);
    }
}
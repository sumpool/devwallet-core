/*
 * Created by Michael Carrara <michael.carrara@breadwallet.com> on 7/1/19.
 * Copyright (c) 2019 Breadwinner AG.  All right reserved.
*
 * See the LICENSE file at the project root for license information.
 * See the CONTRIBUTORS file at the project root for a list of contributors.
 */
package com.breadwallet.corecrypto;

import com.breadwallet.crypto.blockchaindb.BlockchainDb;
import com.breadwallet.crypto.blockchaindb.errors.QueryError;
import com.breadwallet.crypto.blockchaindb.models.bdb.Blockchain;
import com.breadwallet.crypto.blockchaindb.models.bdb.BlockchainFee;
import com.breadwallet.crypto.blockchaindb.models.bdb.CurrencyDenomination;
import com.breadwallet.crypto.utility.CompletionHandler;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/* package */
final class NetworkDiscovery {

    private static final Logger Log = Logger.getLogger(NetworkDiscovery.class.getName());

    /* package */
    interface Callback {
        void discovered(Network network);
        void complete(List<com.breadwallet.crypto.Network> networks);
    }

    /* package */
    static void discoverNetworks(BlockchainDb query,
                                 boolean isMainnet,
                                 List<com.breadwallet.crypto.blockchaindb.models.bdb.Currency> appCurrencies,
                                 Callback callback) {
        List<com.breadwallet.crypto.Network> networks = new ArrayList<>();

        CountUpAndDownLatch latch = new CountUpAndDownLatch(() -> callback.complete(networks));

        // The 'supportedNetworks' will be builtin networks matching isMainnet.
        List<Network> supportedNetworks = findBuiltinNetworks(isMainnet);

        getBlockChains(latch, query, isMainnet, remoteModels -> {
            // We ONLY support built-in blockchains; but the remotes have some
            // needed values - specifically the network fees.

            // Filter `remote` to only include `builtin` block chains.  Thus `remote` will never
            // have more than `builtin` but might have less.
            Collection<Blockchain> supportedModels = filterBlockchains(supportedNetworks, remoteModels);

            // If there are no supported models - typically because the network query failed -
            // then simply announce the suppported networks.
            if (supportedModels.isEmpty()) {
                for (Network network: supportedNetworks) {
                    // Announce the network
                    callback.discovered(network);

                    // Keep a running total of discovered networks
                    networks.add(network);
                }
                return null;
            }

            // Otherwise, process each supportedModel and fill our a supported network
            for (Blockchain blockchainModel : supportedModels) {
                String blockchainModelId = blockchainModel.getId();

                final Network network = findNetwork (supportedNetworks, blockchainModelId);
                if (null == network) {
                    Log.log(Level.FINE, String.format("Missed Network for Model: %s", blockchainModelId));
                    break;
                }

                @SuppressWarnings("OptionalGetWithoutIsPresent")
                UnsignedLong blockHeight = blockchainModel.getBlockHeight().get();

                final List<com.breadwallet.crypto.blockchaindb.models.bdb.Currency> applicationCurrencies = new ArrayList<>();
                for (com.breadwallet.crypto.blockchaindb.models.bdb.Currency currency: appCurrencies) {
                    if (currency.getBlockchainId().equals(blockchainModelId)) {
                        applicationCurrencies .add(currency);
                    }
                }

                getCurrencies(latch, query, blockchainModelId, applicationCurrencies, currencyModels -> {
                    for (com.breadwallet.crypto.blockchaindb.models.bdb.Currency currencyModel : currencyModels) {
                        Currency currency = Currency.create(
                                currencyModel.getId(),
                                currencyModel.getName(),
                                currencyModel.getCode(),
                                currencyModel.getType(),
                                currencyModel.getAddress().orNull());

                        Optional<CurrencyDenomination> baseDenomination = findFirstBaseDenomination(currencyModel.getDenominations());
                        List<CurrencyDenomination> nonBaseDenominations = findAllNonBaseDenominations(currencyModel.getDenominations());

                        Unit baseUnit = baseDenomination.isPresent() ? currencyDenominationToBaseUnit(currency, baseDenomination.get()) :
                                currencyToDefaultBaseUnit(currency);

                        List<Unit> units = currencyDenominationToUnits(currency, nonBaseDenominations, baseUnit);

                        units.add(0, baseUnit);
                        Collections.sort(units, (o1, o2) -> o2.getDecimals().compareTo(o1.getDecimals()));
                        Unit defaultUnit = units.get(0);

                        network.addCurrency(currency, baseUnit, defaultUnit);
                        for (Unit u: units) {
                            network.addUnitFor(currency, u);
                        }
                    }

                    Unit feeUnit = network.baseUnitFor (network.getCurrency()).orNull();
                    if (null == feeUnit) { /* never here */ return null; }

                    List<NetworkFee> fees = new ArrayList<>();
                    for (BlockchainFee bdbFee: blockchainModel.getFeeEstimates()) {
                        Optional<Amount> amount = Amount.create(bdbFee.getAmount(), false, feeUnit);
                        if (amount.isPresent()) {
                            fees.add(NetworkFee.create(bdbFee.getConfirmationTimeInMilliseconds(), amount.get()));
                        }
                    }

                    if (fees.isEmpty()) {
                        Log.log(Level.FINE, String.format("Missed Fees %s", blockchainModel.getName()));
                        return null;
                    }

                    network.setFees(fees);

                    // Announce the network
                    callback.discovered(network);

                    // Keep a running total of discovered networks
                    networks.add(network);

                    return null;
                });
            }
            return null;
        });
    }

    private static Collection<Blockchain> filterBlockchains(Collection<Network> supported,
                                                            Collection<Blockchain> remotes) {

        Map<String, Network> supportedMap = new HashMap<>();
        for (Network b: supported) {
            supportedMap.put(b.getUids(), b);
        }

        Map<String, Blockchain> filteredMap = new HashMap<>();
        for (Blockchain b: remotes) {
            String id = b.getId();
            if (supportedMap.containsKey(id)) {
                filteredMap.put(id, b);
            }
        }

        return filteredMap.values();
    }

    private static void getBlockChains(CountUpAndDownLatch latch,
                                       BlockchainDb query,
                                       boolean isMainnet,
                                       Function<Collection<Blockchain>, Void> func) {
        latch.countUp();
        query.getBlockchains(isMainnet, new CompletionHandler<List<Blockchain>, QueryError>() {
            @Override
            public void handleData(List<Blockchain> remote) {
                try {
                    List<Blockchain> blockchains = new ArrayList<>(remote.size());
                    for (Blockchain blockchain: remote) {
                        if (blockchain.getBlockHeight().isPresent()) {
                            blockchains.add(blockchain);
                        }
                    }
                    func.apply(blockchains);
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void handleError(QueryError error) {
                try {
                    func.apply(Collections.emptyList());
                } finally {
                    latch.countDown();
                }
            }
        });
    }

    private static void getCurrencies(CountUpAndDownLatch latch,
                                      BlockchainDb query,
                                      String blockchainId,
                                      Collection<com.breadwallet.crypto.blockchaindb.models.bdb.Currency> applicationCurrencies,
                                      Function<Collection<com.breadwallet.crypto.blockchaindb.models.bdb.Currency>, Void> func) {
        latch.countUp();
        query.getCurrencies(blockchainId, new CompletionHandler<List<com.breadwallet.crypto.blockchaindb.models.bdb.Currency>, QueryError>() {
            @Override
            public void handleData(List<com.breadwallet.crypto.blockchaindb.models.bdb.Currency> newCurrencies) {
                try {
                    // On success, always merge `default` INTO the result.  We merge defaultUnit
                    // into `result` to always bias to the blockchainDB result.

                    Map<String, com.breadwallet.crypto.blockchaindb.models.bdb.Currency> merged = new HashMap<>();
                    for (com.breadwallet.crypto.blockchaindb.models.bdb.Currency currency : newCurrencies) {
                        if (currency.getBlockchainId().equals(blockchainId) && currency.getVerified()) {
                            merged.put(currency.getId(), currency);
                        }
                    }

                    func.apply(merged.values());
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void handleError(QueryError error) {
                try {
                    // On error, use `apps` merged INTO defaults.  We merge into `defaults` to ensure that we get
                    // BTC, BCH, ETH, BRD and that they are correct (don't rely on the App).

                    Map<String, com.breadwallet.crypto.blockchaindb.models.bdb.Currency> merged = new HashMap<>();
                    for (com.breadwallet.crypto.blockchaindb.models.bdb.Currency currency : applicationCurrencies) {
                        if (currency.getBlockchainId().equals(blockchainId) && currency.getVerified()) {
                            merged.put(currency.getId(), currency);
                        }
                    }

                    func.apply(merged.values());
                } finally {
                    latch.countDown();
                }
            }
        });
    }

    private static Optional<CurrencyDenomination> findFirstBaseDenomination(List<CurrencyDenomination> denominations) {
        for (CurrencyDenomination denomination : denominations) {
            if (denomination.getDecimals().equals(UnsignedInteger.ZERO)) {
                return Optional.of(denomination);
            }
        }
        return Optional.absent();
    }

    private static List<CurrencyDenomination> findAllNonBaseDenominations(List<CurrencyDenomination> denominations) {
        List<CurrencyDenomination> newDenominations = new ArrayList<>();
        for (CurrencyDenomination denomination : denominations) {
            if (!denomination.getDecimals().equals(UnsignedInteger.ZERO)) {
                newDenominations.add(denomination);
            }
        }
        return newDenominations;
    }

    private static Unit currencyToDefaultBaseUnit(Currency currency) {
        String symb = currency.getCode().toLowerCase(Locale.ROOT) + "i";
        String name = currency.getCode().toUpperCase(Locale.ROOT) + "_INTEGER";
        String uids = String.format("%s:%s", currency.getUids(), name);
        return Unit.create(currency, uids, name, symb);
    }

    private static Unit currencyDenominationToBaseUnit(Currency currency,
                                                       CurrencyDenomination denomination) {
        String uids = String.format("%s:%s", currency.getUids(), denomination.getCode());
        return Unit.create(currency, uids, denomination.getName(), denomination.getSymbol());
    }

    private static List<Unit> currencyDenominationToUnits(Currency currency,
                                                          List<CurrencyDenomination> denominations,
                                                          Unit base) {
        List<Unit> units = new ArrayList<>();
        for (CurrencyDenomination denomination : denominations) {
            String uids = String.format("%s:%s", currency.getUids(), denomination.getCode());
            units.add(Unit.create(currency, uids, denomination.getName(), denomination.getSymbol(), base,
                      denomination.getDecimals()));
        }
        return units;
    }

    private static Optional<Currency> findCurrency(Map<Currency,
            NetworkAssociation> associations, Blockchain blockchainModel) {
        String code = blockchainModel.getCurrency().toLowerCase(Locale.ROOT);
        for (Currency currency : associations.keySet()) {
            if (code.equals(currency.getUids())) {
                return Optional.of(currency);
            }
        }
        return Optional.absent();
    }

    private static List<Network> findBuiltinNetworks(boolean isMainnet) {
        List<Network> builtins = Network.installBuiltins();
        List<Network> networks = new ArrayList<>();
        for (Network n: builtins) {
            if (isMainnet == n.isMainnet())
                networks.add(n);
        }
        return networks;
    }

    private static Network findNetwork (List<Network> supportedNetworks, String id) {
        for (Network n : supportedNetworks) {
            if (id.equals(n.getUids()))
                return n;
        }
        return null;
    }

    private static class CountUpAndDownLatch {

        private final Runnable runnable;
        private final AtomicInteger count;

        CountUpAndDownLatch(Runnable runnable) {
            this.count = new AtomicInteger(0);
            this.runnable = runnable;
        }

        void countUp() {
            count.getAndIncrement();
        }

        void countDown() {
            if (0 == count.decrementAndGet()) {
                runnable.run();
            }
        }
    }
}
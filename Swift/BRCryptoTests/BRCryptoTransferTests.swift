//
//  BRCryptoTransferTests.swift
//  BRCryptoTests
//
//  Created by Ed Gamble on 1/11/19.
//  Copyright © 2019 Breadwallet AG. All rights reserved.
//
//  See the LICENSE file at the project root for license information.
//  See the CONTRIBUTORS file at the project root for a list of contributors.
//

import XCTest
@testable import BRCrypto

struct TransferResult {
    let target: Bool
    let address: String
    let confirmation: TransferConfirmation
    let hash: String
    let amount: UInt64

    func validate (transfer: Transfer) -> Bool {
        guard address == (target ? transfer.target : transfer.source)?.description
            else { return false }

        guard let transferHash = transfer.hash?.description, self.hash == transferHash
            else { return false }

        guard let transferInteger = transfer.amount.integerRawSmall, amount == transferInteger
            else { return false }

       guard let transferConfirmation = transfer.confirmation // , self.confirmation == transferConfirmation
            else { return false }

         // If we have a result confirmation fee, compare to transfer fee
        guard nil == confirmation.fee || confirmation.fee == transfer.fee
            else { return false }

        // If the transfer's confirmations's transactionIndex is non-zero, compare it
        guard 0 == transferConfirmation.transactionIndex ||
            confirmation.transactionIndex == transferConfirmation.transactionIndex
            else { return false }

        guard transferConfirmation.blockNumber == confirmation.blockNumber,
            transferConfirmation.timestamp == confirmation.timestamp
            else { return false }

        return true
    }
}

class BRCryptoTransferTests: BRCryptoSystemBaseTests {
    let syncTimeoutInSeconds = 120.0

    /// Recv: 0.00010000
    ///
    /// Address: https://live.blockcypher.com/btc-testnet/address/mzjmRwzABk67iPSrLys1ACDdGkuLcS6WQ4/
    /// Transactions:
    ///    0: https://live.blockcypher.com/btc-testnet/tx/f6d9bca3d4346ce75c151d1d8f061d56ff25e41a89553544b80d316f7d9ccedc/
    ///    1:
    let knownAccountSpecification = AccountSpecification (dict: [
        "identifier": "general",
        "paperKey":   "general shaft mirror pave page talk basket crumble thrive gaze bamboo maid",
        "timestamp":  "2019-08-15",
        "network":    "testnet"
    ])

    let knownTransferResults: [TransferResult] = [
        // P2P timestamp is: 1565974068 - will fail.
        TransferResult (target: true,
                        address: "mzjmRwzABk67iPSrLys1ACDdGkuLcS6WQ4",
                        confirmation: TransferConfirmation (blockNumber: 1574853,
                                                            transactionIndex: 26,
                                                            timestamp: 1565974410, // 2019-08-16T16:53:30Z
                            fee: nil),
                        hash: "f6d9bca3d4346ce75c151d1d8f061d56ff25e41a89553544b80d316f7d9ccedc",
                        amount: UInt64(1000000))
    ]

    override func setUp() {
        super.setUp()
    }

    override func tearDown() {
    }

    /// MARK: - BTC

    func runTransferBTCTest (mode: WalletManagerMode) {
        isMainnet = false
        currencyCodesNeeded = ["btc"]
        modeMap = ["btc":mode]
        prepareAccount (knownAccountSpecification)
        prepareSystem()

        let walletManagerDisconnectExpectation = XCTestExpectation (description: "Wallet Manager Disconnect")
        listener.managerHandlers += [
            { (system: System, manager:WalletManager, event: WalletManagerEvent) in
                if case let .changed(_, newState) = event, case .disconnected = newState {
                    walletManagerDisconnectExpectation.fulfill()
                }
            }]


        let network: Network! = system.networks.first { "btc" == $0.currency.code && isMainnet == $0.isMainnet }
        XCTAssertNotNil (network)

        let manager: WalletManager! = system.managers.first { $0.network == network }
        XCTAssertNotNil (manager)
        manager.addressScheme = AddressScheme.btcLegacy

        let wallet = manager.primaryWallet
        XCTAssertNotNil(wallet)

        // Connect and wait for a number of transfers
        listener.transferIncluded = true
        listener.transferCount = knownTransferResults.count
        manager.connect()
        wait (for: [listener.transferExpectation], timeout: syncTimeoutInSeconds)

        manager.disconnect()
        wait (for: [walletManagerDisconnectExpectation], timeout: 5)

        let transfers = wallet.transfers
        XCTAssertEqual  (transfers.count, knownTransferResults.count)

        XCTAssertTrue (zip (knownTransferResults, transfers).reduce(true) {
            (result:Bool, pair:(TransferResult, Transfer)) -> Bool in
            return result && pair.0.validate(transfer: pair.1)
        })

        let transfer = transfers[0]
        XCTAssertNotNil (transfer.confirmation)
        XCTAssertNotNil (transfer.hash)

        XCTAssertNotNil (wallet.transferBy(hash: transfer.hash!))
        XCTAssertNotNil (wallet.transferBy(core: transfer.core))

        // Events
        
        XCTAssertTrue (listener.checkSystemEvents(
            [EventMatcher (event: SystemEvent.created),
             EventMatcher (event: SystemEvent.networkAdded(network: network), strict: true, scan: true),
             EventMatcher (event: SystemEvent.managerAdded(manager: manager), strict: true, scan: true)
            ]))

        XCTAssertTrue (listener.checkManagerEvents(
            [EventMatcher (event: WalletManagerEvent.created),
             EventMatcher (event: WalletManagerEvent.walletAdded(wallet: wallet)),
             EventMatcher (event: WalletManagerEvent.changed(oldState: WalletManagerState.created,   newState: WalletManagerState.connected)),
             EventMatcher (event: WalletManagerEvent.syncStarted),
             EventMatcher (event: WalletManagerEvent.changed(oldState: WalletManagerState.connected, newState: WalletManagerState.syncing)),

             // Not in API_MODE
             // EventMatcher (event: WalletManagerEvent.syncProgress(timestamp: nil, percentComplete: 0), strict: false),
             EventMatcher (event: WalletManagerEvent.walletChanged(wallet: wallet), strict: true, scan: true),

             EventMatcher (event: WalletManagerEvent.syncEnded(error: nil), strict: false, scan: true),
             EventMatcher (event: WalletManagerEvent.changed(oldState: WalletManagerState.syncing, newState: WalletManagerState.connected)),
             EventMatcher (event: WalletManagerEvent.changed(oldState: WalletManagerState.connected, newState: WalletManagerState.disconnected))
            ]))
        
        XCTAssertTrue (
            listener.checkWalletEvents ([EventMatcher (event: WalletEvent.created),
                                         EventMatcher (event: WalletEvent.transferAdded(transfer: transfer), strict: true, scan: true),
                                         EventMatcher (event: WalletEvent.balanceUpdated(amount: wallet.balance), strict: true, scan: true)])
                || listener.checkWalletEvents ([EventMatcher (event: WalletEvent.created),
                                                EventMatcher (event: WalletEvent.balanceUpdated(amount: wallet.balance), strict: true, scan: true),
                                                EventMatcher (event: WalletEvent.transferAdded(transfer: transfer), strict: true, scan: true)])
        )

        XCTAssertTrue (listener.checkTransferEvents(
            [EventMatcher (event: TransferEvent.created),
             EventMatcher (event: TransferEvent.changed(old: TransferState.created,
                                                        new: TransferState.included(confirmation: transfer.confirmation!)))
                ]))
    }

    func testTransferBTC_API() {
        runTransferBTCTest(mode: WalletManagerMode.api_only)
    }

    func testTransferBTC_P2P() {
        runTransferBTCTest(mode: WalletManagerMode.p2p_only)
    }

    /// MARK: - BCH

    func testTransferBCH_P2P () {
        isMainnet = true
        currencyCodesNeeded = ["bch"]
        modeMap = ["bch":WalletManagerMode.p2p_only]
        prepareAccount (identifier: "loan")
        prepareSystem()

        let walletManagerDisconnectExpectation = XCTestExpectation (description: "Wallet Manager Disconnect")
        listener.managerHandlers += [
            { (system: System, manager:WalletManager, event: WalletManagerEvent) in
                if case let .changed(_, newState) = event, case .disconnected = newState {
                    walletManagerDisconnectExpectation.fulfill()
                }
            }]

        let network: Network! = system.networks.first { "bch" == $0.currency.code && isMainnet == $0.isMainnet }
        XCTAssertNotNil (network)

        let manager: WalletManager! = system.managers.first { $0.network == network }
        XCTAssertNotNil (manager)
        manager.addressScheme = AddressScheme.btcLegacy

        let wallet = manager.primaryWallet
        XCTAssertNotNil(wallet)

        // Connect and wait for a number of transfers
        listener.transferIncluded = true
        listener.transferCount = 1
        manager.connect()
        wait (for: [listener.transferExpectation], timeout: syncTimeoutInSeconds)

        manager.disconnect()
        wait (for: [walletManagerDisconnectExpectation], timeout: 5)

        XCTAssertTrue (wallet.transfers.count > 0)
        let transfer = wallet.transfers[0]
        XCTAssertTrue (nil != transfer.source || nil != transfer.target)
        if let source = transfer.source {
            XCTAssertTrue (source.description.starts (with: (isMainnet ? "bitcoincash" : "bchtest")))
        }
        if let target = transfer.target {
            XCTAssertTrue (target.description.starts (with: (isMainnet ? "bitcoincash" : "bchtest")))
        }
    }
    
    /// MARK: - ETH

    func runTransferETHTest () {
        let network: Network! = system.networks.first { "eth" == $0.currency.code && isMainnet == $0.isMainnet }
        XCTAssertNotNil (network)

        let manager: WalletManager! = system.managers.first { $0.network == network }
        XCTAssertNotNil (manager)

        let wallet = manager.primaryWallet
        XCTAssertNotNil(wallet)

        // Connect and wait for a number of transfers
        listener.transferCount = 3
        manager.connect()
        wait (for: [listener.transferExpectation], timeout: 70)

        XCTAssertFalse (wallet.transfers.isEmpty)
        XCTAssertTrue  (wallet.transfers.count >= 3)
        let t0 = wallet.transfers[0]
        let t1  = wallet.transfers[1]

        XCTAssertTrue  (t0.identical(that: t0))
        XCTAssertFalse (t0.identical(that: t1))
        XCTAssertEqual (t0, t0)
        XCTAssertNotEqual (t0, t1)

        XCTAssertTrue  (t0.system === system)
        XCTAssertEqual (t0.wallet,  wallet)
        XCTAssertEqual (t0.manager, manager)
        XCTAssertNotNil(t0.source)
        XCTAssertNotNil(t0.target)
        XCTAssertNotNil (t0.confirmedFeeBasis)

        XCTAssertNotNil (t0.confirmation)
        // confirmations
        // confirmationsAs
        let t0c = t0.confirmation!

        // Fails until TransferState(core: ...) fills in Fee - requires unit...CORE-421
        XCTAssertNotNil (t0c.fee)

        XCTAssertNotNil (t0.hash)
        XCTAssertNotNil (t1.hash)
        XCTAssertEqual (t0.hash, t0.hash)
        XCTAssertNotEqual(t0.hash, t1.hash)
        let _: [TransferHash:Int] = [t0.hash!:0, t1.hash!:1]

        if case .included = t0.state {} else { XCTAssertTrue (false)}
        // direction

    }

    func testTransferETH_API () {
        isMainnet = false
        currencyCodesNeeded = ["eth"]
        modeMap = ["eth":WalletManagerMode.api_only]
        prepareAccount (AccountSpecification (dict: [
            "identifier": "ginger",
            "paperKey":   "ginger settle marine tissue robot crane night number ramp coast roast critic",
            "timestamp":  "2018-01-01",
            "network":    (isMainnet ? "mainnet" : "testnet")
            ]))
        prepareSystem()

        runTransferETHTest()
    }

    
    func testTransferConfirmation () {
        let btc = Currency (uids: "Bitcoin",  name: "Bitcoin",  code: "BTC", type: "native", issuer: nil)
        //let BTC_SATOSHI = BRCrypto.Unit (currency: btc, uids: "BTC-SAT",  name: "Satoshi", symbol: "SAT")

        let confirmation = TransferConfirmation (blockNumber: 1,
                                                 transactionIndex: 2,
                                                 timestamp: 3,
                                                 fee: nil)
        XCTAssertEqual(1, confirmation.blockNumber)
        XCTAssertEqual(2, confirmation.transactionIndex)
        XCTAssertEqual(3, confirmation.timestamp)
        XCTAssertNil(confirmation.fee)
    }

    func testTransferDirection () {
        XCTAssertEqual(TransferDirection.sent,      TransferDirection (core: TransferDirection.sent.core))
        XCTAssertEqual(TransferDirection.received,  TransferDirection (core: TransferDirection.received.core))
        XCTAssertEqual(TransferDirection.recovered, TransferDirection (core: TransferDirection.recovered.core))
    }

    func testTransferHash () {
    }

    func testTransferFeeBasis () {
    }

    func testTransferState () {
        // XCTAssertEqual (TransferState.created, TransferState(core: CRYPTO_TRANSFER_STATE_CREATED))
        // ...
    }

}

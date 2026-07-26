// The flipped direction: the Mac listens, the phone hosts.
//
// This is the only way to verify the Android host path on this hardware. With no second
// Android device, nothing else can confirm that what the phone puts on the air is
// well-formed and decodes to the instance and code its own screen is showing.
//
// It scans unfiltered and matches the magic in code, because CoreBluetooth cannot express
// Android's prefix-mask filter — `scanForPeripherals(withServices:)` takes whole UUIDs, and
// the whole UUID changes every 30 seconds by design.
//
//   swiftc -O BukInProtocol.swift scan.swift -o scan
//   ./scan [--instancia 42] [--key 000102...0f]
//
// Given a key it also checks the code against its own derivation, which turns the output
// from "something is broadcasting" into "the phone and this Mac agree on the protocol".
import Foundation
import CoreBluetooth

final class Scanner: NSObject, CBCentralManagerDelegate {

    private var manager: CBCentralManager!
    private let key: [UInt8]?
    private let expectedInstancia: UInt32?
    private var seen = 0

    init(key: [UInt8]?, expectedInstancia: UInt32?) {
        self.key = key
        self.expectedInstancia = expectedInstancia
    }

    func start() {
        manager = CBCentralManager(delegate: self, queue: nil)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("STATE: poweredOn — scanning for 42554B4E-… ")
            central.scanForPeripherals(
                withServices: nil,
                options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
            )
        case .unauthorized:
            print("STATE: unauthorized — grant this terminal Bluetooth in System Settings")
            exit(2)
        case .poweredOff:
            print("STATE: poweredOff — switch Bluetooth on")
            exit(3)
        default:
            print("STATE: \(central.state.rawValue)")
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard let uuids = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] else {
            return
        }
        for uuid in uuids {
            guard let decoded = BukIn.decode(uuidString: uuid.uuidString) else { continue }
            seen += 1

            var verdict = ""
            if let key = key {
                let now = Int64(Date().timeIntervalSince1970)
                let counter = BukIn.counter(forUnixSeconds: now)
                // Accept counter ± 1, the same RFC 6238 §5.2 tolerance the server uses.
                let matched = [counter - 1, counter, counter + 1].first { candidate in
                    BukIn.derive(key: key, instanciaId: decoded.instanciaId, counter: candidate) == decoded.code
                }
                verdict = matched.map { "  CODE OK (counter=\($0))" } ?? "  code does not match this key"
            }
            var instanciaNote = ""
            if let expected = expectedInstancia, expected != decoded.instanciaId {
                instanciaNote = "  ⚠️ expected instancia \(expected)"
            }

            print("[\(BukIn.timestamp())] instancia_id=\(decoded.instanciaId) "
                + "code=\(BukIn.hex(decoded.code)) rssi=\(RSSI)\(verdict)\(instanciaNote)")
        }
    }
}

@main
enum Main {
    static func main() {
        // stdout is fully buffered when it is not a terminal and this process never exits.
        setvbuf(stdout, nil, _IONBF, 0)

        let (key, expectedInstancia) = BukIn.parseArguments()

        BukIn.selfCheck()
        if key == nil {
            // The host generates its key with SecureRandom and never discloses it, so this
            // is the normal case: report what is on the air and leave validity to the server.
            print("no --key given: decoding only, not checking the code")
        }
        print("ctrl-C to stop")

        let scanner = Scanner(key: key, expectedInstancia: expectedInstancia)
        scanner.start()
        RunLoop.main.run()
    }
}

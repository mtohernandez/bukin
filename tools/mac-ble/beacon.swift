// The Mac as co-host.
//
// The project has one Android phone and no second Android device, so this stands in as the
// beacon the collaborator flow is tested against. That is not a hack around the
// architecture — it is the architecture: "any device holding the instance key can advertise
// the same code", which is also the documented answer to a room too large for one host.
//
// It works at all because the payload rides inside the 128-bit service UUID.
// CBPeripheralManager refuses service data and manufacturer data, so a service-data design
// would have made this impossible and the host role permanently Android-only.
//
//   swiftc -O BukInProtocol.swift beacon.swift -o beacon
//   ./beacon [--instancia 42] [--key 000102...0f]
import Foundation
import CoreBluetooth

final class Beacon: NSObject, CBPeripheralManagerDelegate {

    private var manager: CBPeripheralManager!
    private let key: [UInt8]
    private let instanciaId: UInt32
    private var currentCounter: Int64 = -1

    init(key: [UInt8], instanciaId: UInt32) {
        self.key = key
        self.instanciaId = instanciaId
    }

    func start() {
        manager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            print("STATE: poweredOn")
            rotate()
        case .unauthorized:
            print("STATE: unauthorized — grant this terminal Bluetooth in System Settings")
            exit(2)
        case .poweredOff:
            print("STATE: poweredOff — switch Bluetooth on")
            exit(3)
        default:
            print("STATE: \(peripheral.state.rawValue)")
        }
    }

    /// Re-derive and re-advertise, then schedule the next rotation on the window boundary.
    ///
    /// Aligned to the boundary rather than a flat 30-second timer: drifting would eventually
    /// put this beacon and the phone on different counters, and the symptom would be
    /// intermittent rejections that look like a radio problem.
    private func rotate() {
        let now = Int64(Date().timeIntervalSince1970)
        let counter = BukIn.counter(forUnixSeconds: now)
        let code = BukIn.derive(key: key, instanciaId: instanciaId, counter: counter)
        let uuid = BukIn.uuidString(instanciaId: instanciaId, code: code)

        currentCounter = counter
        manager.stopAdvertising()
        manager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [CBUUID(string: uuid)]
        ])

        print("[\(BukIn.timestamp())] counter=\(counter) code=\(BukIn.hex(code)) uuid=\(uuid)")

        let nextBoundary = Double((counter + 1) * BukIn.windowSeconds)
        let delay = max(0.5, nextBoundary - Date().timeIntervalSince1970)
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            self?.rotate()
        }
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            print("ADVERTISE FAILED: \(error.localizedDescription)")
            exit(1)
        }
    }
}

// stdout is fully buffered when it is not a terminal, and this process never exits — so
// without this every line would sit in the buffer and the tool would look hung.
@main
enum Main {
    static func main() {
        setvbuf(stdout, nil, _IONBF, 0)

        // Defaults are the known vector's key and instancia, so the beacon lines up with
        // both RotatingCodeTest and what scan.swift expects with no flags at all.
        let (parsedKey, parsedInstancia) = BukIn.parseArguments()
        let key = parsedKey ?? BukIn.vectorKey
        let instanciaId = parsedInstancia ?? BukIn.vectorInstancia

        BukIn.selfCheck()
        print("beacon: instancia=\(instanciaId) key=\(BukIn.hex(key))")
        print("rotating every \(BukIn.windowSeconds)s — ctrl-C to stop")

        let beacon = Beacon(key: key, instanciaId: instanciaId)
        beacon.start()
        RunLoop.main.run()
    }
}

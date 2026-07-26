// Probe: can this Mac advertise a custom 128-bit service UUID the way the BukIn host
// would? The payload lives inside the UUID, which is the only thing CoreBluetooth lets a
// non-iOS-app advertise, so this is the exact shape session 2 needs.
import Foundation
import CoreBluetooth

final class Probe: NSObject, CBPeripheralManagerDelegate {
    var manager: CBPeripheralManager!
    // BUKN | instancia_id | code — same 16-byte layout as the spec.
    let serviceUUID = CBUUID(string: "42554B4E-0001-0002-0003-A1B2C3D4E5F6")

    func start() {
        manager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            print("STATE: poweredOn")
            peripheral.startAdvertising([
                CBAdvertisementDataServiceUUIDsKey: [serviceUUID]
            ])
        case .unauthorized:
            print("STATE: unauthorized — Bluetooth permission not granted to this binary")
            exit(2)
        case .poweredOff:
            print("STATE: poweredOff")
            exit(3)
        default:
            print("STATE: \(peripheral.state.rawValue)")
        }
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            print("ADVERTISE FAILED: \(error.localizedDescription)")
            exit(1)
        }
        print("ADVERTISE OK: \(serviceUUID.uuidString)")
        print("isAdvertising = \(peripheral.isAdvertising)")
        exit(0)
    }
}

let probe = Probe()
probe.start()
// Give CoreBluetooth a few seconds to answer, then fail loudly rather than hang.
DispatchQueue.global().asyncAfter(deadline: .now() + 10) {
    print("TIMEOUT: no delegate callback within 10s")
    exit(4)
}
RunLoop.main.run()

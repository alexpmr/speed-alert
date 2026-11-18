// Play Asset Delivery pack with an offline OSM database (osm/<region>.sqlite).
// The .sqlite is restored by CI from data/osm-regions.lock; it is never committed.
plugins { id("com.android.asset-pack") }
assetPack {
    packName.set("osm_sudeste")
    dynamicDelivery { deliveryType.set("on-demand") }
}

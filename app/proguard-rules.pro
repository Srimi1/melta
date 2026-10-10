# Melta keeps no reflection based code. Default Android optimize rules are enough.
# Keep the overlay view constructor in case it is ever inflated from XML.
-keep class com.melta.app.battery.overlay.BatteryBarView { <init>(...); }

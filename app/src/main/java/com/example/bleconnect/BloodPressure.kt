package com.example.bleconnect

import android.os.Parcel
import android.os.Parcelable

class BloodPressure constructor(var systolic: Int, var diastolic: Int, var pulse: Int) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt()
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(systolic)
        parcel.writeInt(diastolic)
        parcel.writeInt(pulse)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BloodPressure> {
        override fun createFromParcel(parcel: Parcel): BloodPressure {
            return BloodPressure(parcel)
        }

        override fun newArray(size: Int): Array<BloodPressure?> {
            return arrayOfNulls(size)
        }
    }
}
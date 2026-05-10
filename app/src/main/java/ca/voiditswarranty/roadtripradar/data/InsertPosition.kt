package ca.voiditswarranty.roadtripradar.data

sealed class InsertPosition {
    object Start : InsertPosition()
    object BeforeLast : InsertPosition()
    object End : InsertPosition()
    data class Index(val i: Int) : InsertPosition()
    data class ReplaceId(val id: String) : InsertPosition()
}

package com.jimmyyu.qvgallery.ui.viewer

/** Lets the viewer receive hardware key presses the Activity sees first (volume keys). */
object KeyEvents {
    @Volatile
    var handler: ((keyCode: Int) -> Boolean)? = null
}

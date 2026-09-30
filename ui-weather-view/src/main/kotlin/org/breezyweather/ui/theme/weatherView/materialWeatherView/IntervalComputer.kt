/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.theme.weatherView.materialWeatherView

class IntervalComputer {
    private var mCurrentTime: Long = 0
    private var mLastTime: Long = 0
    var interval = 0.0
        private set

    init {
        reset()
    }

    fun reset() {
        mCurrentTime = -1
        mLastTime = -1
        interval = 0.0
    }

    fun invalidate() {
        mCurrentTime = System.currentTimeMillis()
        interval = (if (mLastTime == -1L) 0 else mCurrentTime - mLastTime).toDouble()
            .coerceIn(0.0, MAX_INTERVAL_MS)
        mLastTime = mCurrentTime
    }

    private companion object {
        /*
         * VN Weather change: cap one frame's time step. After a stall every
         * drop would jump past the bottom at once and respawn together above
         * the screen, leaving seconds of empty sky before the rain returns.
         */
        const val MAX_INTERVAL_MS = 100.0
    }
}

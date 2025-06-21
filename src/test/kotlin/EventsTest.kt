import kotlin.test.Test
import mindurka.build.StringPtr
import mindurka.api.on
import mindurka.api.Events

class Tests {
    @Test
    fun listenEvent() {
        var works = arrayOf(false)

        class CustomEvent {
            fun works() {
                works[0] = true
            }
        }

        on<CustomEvent> {
            it.works()
        }
        Events.fire(CustomEvent())

        assert(works[0])
    }
}

package com.example.pion.family.tracker.demo.data.di

import com.example.pion.family.tracker.demo.data.location.FusedLocationSource
import com.example.pion.family.tracker.demo.data.location.LiveSelfLocation
import com.example.pion.family.tracker.demo.data.location.LocationPointProcessor
import com.example.pion.family.tracker.demo.data.location.MemberMovementSimulator
import com.example.pion.family.tracker.demo.data.location.SimulatedLocationSource
import com.example.pion.family.tracker.demo.data.network.AndroidNetworkMonitor
import com.example.pion.family.tracker.demo.data.remote.RoutingHttpClient
import com.example.pion.family.tracker.demo.data.repository.MemberRepositoryImpl
import com.example.pion.family.tracker.demo.data.repository.TrackingRepositoryImpl
import com.example.pion.family.tracker.demo.data.repository.ZoneEventRepositoryImpl
import com.example.pion.family.tracker.demo.data.repository.ZoneRepositoryImpl
import com.example.pion.family.tracker.demo.data.routing.GraphHopperRoutingProvider
import com.example.pion.family.tracker.demo.data.routing.MemberRouteSource
import com.example.pion.family.tracker.demo.data.routing.OnDevicePolylineCache
import com.example.pion.family.tracker.demo.data.routing.ValhallaRoutingProvider
import com.example.pion.family.tracker.demo.data.seed.DemoDataSeeder
import com.example.pion.family.tracker.demo.domain.model.RoutingConfig
import com.example.pion.family.tracker.demo.domain.model.RoutingEngine
import com.example.pion.family.tracker.demo.domain.repository.LocationSource
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteProvider
import com.example.pion.family.tracker.demo.domain.repository.NetworkMonitor
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import com.example.pion.family.tracker.demo.domain.repository.SimulatedRouteRepository
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneEventRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.usecase.ObserveNavigationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.PurgeOldHistoryUseCase
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

/** Bindings owned by `:data` — Room, location sources, mô phỏng di chuyển, repository impls. */
val dataModule = module {
    singleOf(::ZoneRepositoryImpl) bind ZoneRepository::class
    // phase-09: thêm SimulatedLocationSource (concrete type, không qua qualifier) để gọi được
    // `.load(fixes)` — xem chú thích ở khối LocationSource bên dưới cho lý do cần cả hai binding.
    // phase-01: + LiveSelfLocation (cổng hiển thị, D4) — `singleOf` nên đây và
    // `LocationPointProcessor` bên dưới nhận CÙNG một instance; hai instance thì chấm xanh câm.
    single { TrackingRepositoryImpl(get(), get(), get(), get(), androidContext()) } bind TrackingRepository::class
    // ZoneEventRepositoryImpl needs a Context to reach ZoneNotifier (LLM.md §8.6) — same explicit
    // `androidContext()` pattern as TrackingRepositoryImpl above, not `singleOf` (LLM.md §6).
    single { ZoneEventRepositoryImpl(get(), get(), get(), androidContext()) } bind ZoneEventRepository::class
    singleOf(::MemberRepositoryImpl) bind MemberRepository::class
    singleOf(::DemoDataSeeder)
    factoryOf(::PurgeOldHistoryUseCase)
    // Routing plan phase-05 — `factoryOf(::ObserveNavigationUseCase)` KHÔNG resolve ở đây:
    // `factoryOf` phản chiếu constructor thật, thấy `nowMs: () -> Long` (tham số thứ 4, có default
    // Kotlin) và đi tìm binding cho `Function0<Long>` — không có, `KoinModulesTest.verify()` đỏ.
    // Viết tay để KHÔNG truyền `nowMs`, Kotlin tự áp default `{ System.currentTimeMillis() }` vì
    // đây là lời gọi constructor trực tiếp, không qua reflection (cùng lý do `TimelineViewModel`'s
    // `clock` KHÔNG áp dụng — `viewModelOf` PHẢI resolve mọi tham số qua `get()`, `factory { }` thì
    // không, LLM.md §6). `locationSource = get(named("fused"))`, KHÔNG `named("simulated")`: vị trí
    // thật của máy người dùng là ý nghĩa của "chỉ đường từ tôi tới X" — không có binding
    // `LocationSource` KHÔNG qualifier trong module này (chỉ "fused"/"simulated"), nên `get()` trần
    // sẽ ném ngay khi màn Navigation mở.
    factory {
        ObserveNavigationUseCase(
            locationSource = get(named("fused")),
            memberRepository = get(),
            routingProvider = get(),
        )
    }

    // Hai LocationSource, chọn bằng qualifier — LLM.md §8.4, phase-04 Key Insight #9.
    single<LocationSource>(named("fused")) { FusedLocationSource(androidContext()) }
    // phase-09 US-33: `SimulatedLocationSource` đăng ký MỘT LẦN dưới kiểu cụ thể của nó rồi bind
    // alias sang `LocationSource(named("simulated"))` — không phải hai `single { SimulatedLocationSource() }`
    // riêng biệt, việc đó sẽ tạo HAI instance khác nhau. `TrackingRepositoryImpl` cần kiểu cụ thể
    // để gọi `.load()` (không có trong interface `LocationSource`, chỉ có `stream()`);
    // `LocationTrackingService` vẫn injects qua qualifier như FusedLocationSource — cả hai phải
    // cùng MỘT instance để `.load()` ở nơi này và `.stream()` ở nơi kia thấy cùng dữ liệu.
    single { SimulatedLocationSource() }
    single<LocationSource>(named("simulated")) { get<SimulatedLocationSource>() }
    // phase-01, D4 — holder trong bộ nhớ cho vị trí thật CHƯA lọc (US-06/US-43).
    singleOf(::LiveSelfLocation)
    singleOf(::LocationPointProcessor)

    // fix-zone-follows-members — nguồn di chuyển của các thành viên được theo dõi, và nơi DUY NHẤT
    // sinh `ZoneEvent` (LLM.md §8.1). `single`, không `factory`: nó giữ trạng thái đi lại
    // (`roamStates`/`insideZoneIds`) giữa các nhịp, hai instance sẽ là hai gia đình đi hai hướng.
    singleOf(::MemberMovementSimulator)

    // Routing plan phase-01 Step 12 — shared HTTP/JSON, no provider (GraphHopper/Valhalla) reads
    // them yet. Timeouts per Requirement #7: no timeout means a job hangs until the service dies.
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
    // `ignoreUnknownKeys = true` is mandatory, not `isLenient` — both GraphHopper and Valhalla
    // return fields this app never declares in a DTO; without this flag every response crashes
    // parsing. Never `isLenient = true`: that would swallow a genuinely broken JSON body as data.
    single<Json> { Json { ignoreUnknownKeys = true } }
    single { RoutingHttpClient(get()) }

    // Routing plan phase-02 Step 7 / phase-03 Step 7. `named("graphhopper")`/`named("valhalla")`
    // are the concrete engine bindings; the unqualified `single<RoutingProvider>` below is the one
    // every use case (phase-04+) actually injects, chosen from `RoutingConfig.engine` by an
    // EXHAUSTIVE `when` — no `else` branch, so adding a third `RoutingEngine` entry is a compile
    // error here, not a silent fallback to GraphHopper.
    single<RoutingProvider>(named("graphhopper")) { GraphHopperRoutingProvider(get(), get(), get()) }
    single<RoutingProvider>(named("valhalla")) { ValhallaRoutingProvider(get(), get(), get()) }
    single<RoutingProvider> {
        when (get<RoutingConfig>().engine) {
            RoutingEngine.GRAPHHOPPER -> get(named("graphhopper"))
            RoutingEngine.VALHALLA -> get(named("valhalla"))
        }
    }

    // Routing plan phase-04 (D5, `decisions.md` §C2) — nguồn tuyến 3 tầng của chuyển động gia đình.
    // `OnDevicePolylineCache` nhận thẳng một `File` (routes dir), KHÔNG `Context`: lớp đó phải chạy
    // được trên JVM thuần (`OnDevicePolylineCacheTest`, LLM.md §11 — không Robolectric), nên KHÔNG
    // `singleOf` (constructor không có tham số `Context` để nó tự resolve qua `androidContext()`).
    // `File` tính MỘT LẦN ở đây, ngay lúc đăng ký Koin — cùng mẫu explicit lambda mà
    // `TrackingRepositoryImpl`/`FusedLocationSource` dùng cho mọi thứ cần `Context`.
    single { OnDevicePolylineCache(routesDir = File(androidContext().filesDir, "routes"), json = get()) }
    // `single`, `binds` (số nhiều) hai interface KHÔNG liên quan tới nhau — MỘT instance biết CẢ
    // cấp tuyến (`MemberRouteProvider`, đọc bởi `MemberMovementSimulator`) LẪN tầng nguồn hiện tại
    // (`SimulatedRouteRepository`, đọc bởi `:ui` phase-05); tách hai lớp cần một kênh đồng bộ giữa
    // chúng, đúng loại phức tạp không mua được gì (`decisions.md` §C2). KHÔNG `bind A::class bind
    // B::class` (chuỗi infix `bind` đơn): `bind` là `KoinDefinition<out S>.bind(KClass<S>):
    // KoinDefinition<out S>` — hiệp biến trên S, nên bind lần hai đòi `S` tương thích với KIỂU TRẢ
    // VỀ của bind lần đầu (`MemberRouteProvider`), không phải kiểu gốc `MemberRouteSource`; vì
    // `SimulatedRouteRepository` không phải supertype của `MemberRouteProvider`, chuỗi đó KHÔNG biên
    // dịch (`Argument type mismatch`, đã thử thật). `binds(Array<KClass<*>>)` không bị ràng buộc đó.
    single { MemberRouteSource(routingProvider = get(), cache = get()) } binds arrayOf(MemberRouteProvider::class, SimulatedRouteRepository::class)

    // phase-07 (US-47, D8) — viết tay chứ KHÔNG `singleOf(::AndroidNetworkMonitor) bind
    // NetworkMonitor::class`: constructor nhận `Context`, và `verify()` phân tích tĩnh qua
    // constructor sẽ đòi một Koin definition cho `Context` — cùng bẫy đã dính ở
    // `TrackingRepositoryImpl` (LLM.md §6). `KoinModulesTest` không cần thêm `extraTypes`.
    single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
}

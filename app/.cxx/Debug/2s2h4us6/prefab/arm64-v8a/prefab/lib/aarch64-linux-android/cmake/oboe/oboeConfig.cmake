if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "/Users/ashutoshsamal/.gradle/caches/8.10.2/transforms/e6663c2903d3ca874756baf547d08338/transformed/oboe-1.9.0/prefab/modules/oboe/libs/android.arm64-v8a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "/Users/ashutoshsamal/.gradle/caches/8.10.2/transforms/e6663c2903d3ca874756baf547d08338/transformed/oboe-1.9.0/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()


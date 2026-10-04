if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "/Users/ashutoshsamal/.gradle/caches/8.10.2/transforms/d1e0c504476f80808dfca4f42ef3aba4/transformed/oboe-1.9.0/prefab/modules/oboe/libs/android.armeabi-v7a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "/Users/ashutoshsamal/.gradle/caches/8.10.2/transforms/d1e0c504476f80808dfca4f42ef3aba4/transformed/oboe-1.9.0/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()


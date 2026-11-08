set(SndFile_FOUND TRUE)
set(SndFile_VERSION "1.2.2")
set(SndFile_VERSION_MAJOR 1)
set(SndFile_VERSION_MINOR 2)
set(SndFile_VERSION_PATCH 2)
set(SndFile_WITH_EXTERNAL_LIBS TRUE)

get_filename_component(_TYN_SNDFILE_PREFIX "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(_TYN_SNDFILE_LIBDIR "${_TYN_SNDFILE_PREFIX}/lib")
set(_TYN_SNDFILE_INCLUDEDIR "${_TYN_SNDFILE_PREFIX}/include")

if(NOT TARGET SndFile::sndfile)
    add_library(SndFile::sndfile STATIC IMPORTED)
    set_target_properties(SndFile::sndfile PROPERTIES
        IMPORTED_LOCATION "${_TYN_SNDFILE_LIBDIR}/libsndfile.a"
        INTERFACE_INCLUDE_DIRECTORIES "${_TYN_SNDFILE_INCLUDEDIR}"
        INTERFACE_LINK_LIBRARIES
            "${_TYN_SNDFILE_LIBDIR}/libFLAC.a;${_TYN_SNDFILE_LIBDIR}/libvorbisenc.a;${_TYN_SNDFILE_LIBDIR}/libvorbis.a;${_TYN_SNDFILE_LIBDIR}/libopus.a;${_TYN_SNDFILE_LIBDIR}/libogg.a;m"
    )
endif()

unset(_TYN_SNDFILE_PREFIX)
unset(_TYN_SNDFILE_LIBDIR)
unset(_TYN_SNDFILE_INCLUDEDIR)

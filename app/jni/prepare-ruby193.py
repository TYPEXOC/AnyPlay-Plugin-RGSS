#!/usr/bin/env python3
"""Apply the Android integration needed by the official Ruby 1.9.3 source tree."""

from pathlib import Path
import shutil
import sys


def replace_once(root: Path, relative_path: str, before: str, after: str) -> None:
    path = root / relative_path
    source = path.read_text(encoding="utf-8")
    if after in source:
        return
    if before not in source:
        raise SystemExit(f"Could not locate the expected Ruby source section: {relative_path}")
    path.write_text(source.replace(before, after, 1), encoding="utf-8", newline="\n")


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "ruby193").resolve()
    if not (root / "version.h").is_file() or not (root / "parse.y").is_file():
        raise SystemExit(f"Not a Ruby 1.9.3 source directory: {root}")

    replace_once(
        root,
        "common.mk",
        "\t\tsprintf.$(OBJEXT) \\\n\t\tst.$(OBJEXT) \\\n",
        "\t\tsprintf.$(OBJEXT) \\\n\t\tzlib.$(OBJEXT) \\\n\t\tst.$(OBJEXT) \\\n",
    )
    replace_once(
        root,
        "inits.c",
        "    CALL(version);\n",
        "    CALL(version);\n    CALL(zlib);\n",
    )
    replace_once(
        root,
        "ext/openssl/extconf.rb",
        'have_func("SSLv2_method")\nhave_func("SSLv2_server_method")\nhave_func("SSLv2_client_method")\n',
        'have_func("SSLv2_method")\nhave_func("SSLv2_server_method")\nhave_func("SSLv2_client_method")\n'
        'have_func("SSLv3_method")\nhave_func("SSLv3_server_method")\nhave_func("SSLv3_client_method")\n',
    )
    replace_once(
        root,
        "ext/openssl/ossl_ssl.c",
        "#endif\n"
        "    OSSL_SSL_METHOD_ENTRY(SSLv3),\n"
        "    OSSL_SSL_METHOD_ENTRY(SSLv3_server),\n"
        "    OSSL_SSL_METHOD_ENTRY(SSLv3_client),\n",
        "#endif\n"
        "#if defined(HAVE_SSLV3_METHOD) && defined(HAVE_SSLV3_SERVER_METHOD) && \\\n"
        "        defined(HAVE_SSLV3_CLIENT_METHOD)\n"
        "    OSSL_SSL_METHOD_ENTRY(SSLv3),\n"
        "    OSSL_SSL_METHOD_ENTRY(SSLv3_server),\n"
        "    OSSL_SSL_METHOD_ENTRY(SSLv3_client),\n"
        "#endif\n",
    )
    replace_once(
        root,
        "ext/openssl/ossl_ssl.c",
        "    ossl_ssl_def_const(OP_MICROSOFT_BIG_SSLV3_BUFFER);\n"
        "    ossl_ssl_def_const(OP_MSIE_SSLV2_RSA_PADDING);\n",
        "    ossl_ssl_def_const(OP_MICROSOFT_BIG_SSLV3_BUFFER);\n"
        "#ifdef SSL_OP_MSIE_SSLV2_RSA_PADDING\n"
        "    ossl_ssl_def_const(OP_MSIE_SSLV2_RSA_PADDING);\n"
        "#endif\n",
    )
    replace_once(
        root,
        "dir.c",
        "static VALUE\n"
        "dir_set_pos(VALUE dir, VALUE pos)\n"
        "{\n"
        "    dir_seek(dir, pos);\n"
        "    return pos;\n"
        "}\n",
        "static VALUE\n"
        "dir_set_pos(VALUE dir, VALUE pos)\n"
        "{\n"
        "#ifdef HAVE_SEEKDIR\n"
        "    dir_seek(dir, pos);\n"
        "#else\n"
        "    rb_f_notimplement(0, NULL, dir);\n"
        "#endif\n"
        "    return pos;\n"
        "}\n",
    )

    zlib_source = root / "ext/zlib/zlib.c"
    zlib_target = root / "zlib.c"
    if zlib_source.is_file():
        shutil.copyfile(zlib_source, zlib_target)
    elif not zlib_target.is_file():
        raise SystemExit("The official Ruby zlib source is missing")
    if (root / "ext/zlib").is_dir():
        shutil.rmtree(root / "ext/zlib")


if __name__ == "__main__":
    main()

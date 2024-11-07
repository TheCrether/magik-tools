package nl.ramsolutions.sw.magik.analysis.helpers;

public class FlagFlavor {
  public static final String FLAG_READ = ":read";
  public static final String FLAG_READABLE = ":readable";
  public static final String FLAG_WRITE = ":write";
  public static final String FLAG_WRITABLE = ":writable";
  public static final String FLAVOR_PUBLIC = ":public";
  public static final String FLAVOR_PRIVATE = ":private";
  public static final String FLAVOR_READ_ONLY = ":read_only";
  public static final String TRUE = "_true";
  public static final String FALSE = "_false";

  public static boolean isReadable(String flag) {
    return flag.equals(FLAG_READ) || flag.equals(FLAG_READABLE);
  }

  public static boolean isWritable(String flag) {
    return flag.equals(FLAG_WRITE) || flag.equals(FLAG_WRITABLE);
  }

  public static boolean isPublic(String flavor) {
    return flavor.equals(FLAVOR_PUBLIC) || flavor.equals(FALSE);
  }

  public static boolean isPrivate(String flavor) {
    return flavor.equals(FLAVOR_PRIVATE) || flavor.equals(TRUE);
  }

  public static boolean isReadOnly(String flavor) {
    return flavor.equals(FLAVOR_READ_ONLY);
  }
}

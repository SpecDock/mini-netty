package io.github.specdock.mininetty.util;

import java.nio.channels.SelectionKey;

/**
 * @author specdock
 * @Date 2026/1/18
 * @Time 13:30
 */
public class InterestOpsUtil {
    public static String interestOpsToString(int interestOps){
        switch (interestOps){
            case SelectionKey.OP_ACCEPT:
                return "OP_ACCEPT";
            case SelectionKey.OP_READ:
                return "OP_READ";
            case SelectionKey.OP_WRITE:
                return "OP_WRITE";
            case SelectionKey.OP_CONNECT:
                return "OP_CONNECT";
        }
        return null;
    }
}

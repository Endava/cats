package com.endava.cats.pool;

import com.endava.cats.model.RequestTarget;

record TargetField(String path, String name, RequestTarget.Location location, ValueShape shape) {
}

package com.family.phototransfer.ui.dashboard;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class SenderDashboardViewModel_Factory implements Factory<SenderDashboardViewModel> {
  @Override
  public SenderDashboardViewModel get() {
    return newInstance();
  }

  public static SenderDashboardViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SenderDashboardViewModel newInstance() {
    return new SenderDashboardViewModel();
  }

  private static final class InstanceHolder {
    private static final SenderDashboardViewModel_Factory INSTANCE = new SenderDashboardViewModel_Factory();
  }
}

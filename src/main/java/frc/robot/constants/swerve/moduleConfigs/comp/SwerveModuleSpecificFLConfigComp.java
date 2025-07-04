package frc.robot.constants.swerve.moduleConfigs.comp;

import frc.robot.constants.swerve.moduleConfigs.SwerveModuleSpecificConfigBase;

public class SwerveModuleSpecificFLConfigComp extends SwerveModuleSpecificConfigBase {

    public static SwerveModuleSpecificFLConfigComp instance = null;
    public static SwerveModuleSpecificFLConfigComp getInstance() {
        if (instance == null) {
            instance = new SwerveModuleSpecificFLConfigComp();
        }
        return instance;
    }

    private SwerveModuleSpecificFLConfigComp() {}

    @Override
    public int getDriveCanId() {
        return 5;
    }

    @Override
    public int getSteerCanId() {
        return 3;
    }

    @Override
    public boolean getIsDriveInverted() {
        return true;
    }

    @Override
    public boolean getIsSteerInverted() {
        return true;
    }

    @Override
    public boolean isDriveNeutralModeBrake() {
        return true;
    }

    @Override
    public boolean isSteerNeutralModeBrake() {
        return true;
    }

    @Override
    public int getCancoderCanId() {
        return 4;
    }

    @Override
    public double getCancoderOffsetRotations() {
        return -0.0488;
    }
}

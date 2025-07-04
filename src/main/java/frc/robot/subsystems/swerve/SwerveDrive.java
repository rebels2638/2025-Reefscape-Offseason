package frc.robot.subsystems.swerve;

import static edu.wpi.first.units.Units.Second;
import static edu.wpi.first.units.Units.Seconds;
import static edu.wpi.first.units.Units.Volts;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.littletonrobotics.junction.Logger;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Direction;
import frc.robot.RobotState;
import frc.robot.RobotState.OdometryObservation;
import frc.robot.constants.Constants;
import frc.robot.constants.swerve.drivetrainConfigs.SwerveDrivetrainConfigBase;
import frc.robot.constants.swerve.drivetrainConfigs.SwerveDrivetrainConfigComp;
import frc.robot.constants.swerve.drivetrainConfigs.SwerveDrivetrainConfigProto;
import frc.robot.constants.swerve.drivetrainConfigs.SwerveDrivetrainConfigSim;
import frc.robot.constants.swerve.moduleConfigs.SwerveModuleGeneralConfigBase;
import frc.robot.constants.swerve.moduleConfigs.comp.SwerveModuleGeneralConfigComp;
import frc.robot.constants.swerve.moduleConfigs.comp.SwerveModuleSpecificBLConfigComp;
import frc.robot.constants.swerve.moduleConfigs.comp.SwerveModuleSpecificBRConfigComp;
import frc.robot.constants.swerve.moduleConfigs.comp.SwerveModuleSpecificFLConfigComp;
import frc.robot.constants.swerve.moduleConfigs.comp.SwerveModuleSpecificFRConfigComp;
import frc.robot.constants.swerve.moduleConfigs.proto.SwerveModuleGeneralConfigProto;
import frc.robot.constants.swerve.moduleConfigs.proto.SwerveModuleSpecificBLConfigProto;
import frc.robot.constants.swerve.moduleConfigs.proto.SwerveModuleSpecificFRConfigProto;
import frc.robot.constants.swerve.moduleConfigs.sim.SwerveModuleGeneralConfigSim;
import frc.robot.subsystems.swerve.gyro.GyroIO;
import frc.robot.subsystems.swerve.gyro.GyroIOInputsAutoLogged;
import frc.robot.subsystems.swerve.gyro.GyroIOPigeon2;
import frc.robot.subsystems.swerve.module.ModuleIO;
import frc.robot.subsystems.swerve.module.ModuleIOInputsAutoLogged;
import frc.robot.subsystems.swerve.module.ModuleIOSim;
import frc.robot.subsystems.swerve.module.ModuleIOTalonFX;

public class SwerveDrive extends SubsystemBase {
    private static SwerveDrive instance = null;
    public static SwerveDrive getInstance() {
        if (instance == null) {
            instance = new SwerveDrive();
        }

        return instance;
    }

    public static final double ODOMETRY_FREQUENCY = 250;

    public static final Lock odometryLock = new ReentrantLock();

    private ModuleIO[] modules;
    private ModuleIOInputsAutoLogged[] moduleInputs = {
            new ModuleIOInputsAutoLogged(),
            new ModuleIOInputsAutoLogged(),
            new ModuleIOInputsAutoLogged(),
            new ModuleIOInputsAutoLogged()
    };

    private final GyroIO gyroIO;
    private GyroIOInputsAutoLogged gyroInputs = new GyroIOInputsAutoLogged();

    private ChassisSpeeds desiredRobotRelativeSpeeds = new ChassisSpeeds();
    private ChassisSpeeds obtainableFieldRelativeSpeeds = new ChassisSpeeds();

    double prevLoopTime = Timer.getTimestamp();

    private final SwerveModuleGeneralConfigBase moduleGeneralConfig;
    private final SwerveDrivetrainConfigBase drivetrainConfig;
    private SwerveDriveKinematics kinematics;

    private final SysIdRoutine driveCharacterizationSysIdRoutine;
    private final SysIdRoutine steerCharacterizationSysIdRoutine;

    @SuppressWarnings("static-access")
    private SwerveDrive() {
        switch (Constants.currentMode) {
            case COMP:
                drivetrainConfig = SwerveDrivetrainConfigComp.getInstance();
                moduleGeneralConfig = SwerveModuleGeneralConfigComp.getInstance();

                modules = new ModuleIO[] {
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificFLConfigComp.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificFRConfigComp.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificBLConfigComp.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificBRConfigComp.getInstance())
                };
                
                gyroIO = new GyroIOPigeon2();
                PhoenixOdometryThread.getInstance().start();

                break;

            case PROTO:
                drivetrainConfig = SwerveDrivetrainConfigProto.getInstance();
                moduleGeneralConfig = SwerveModuleGeneralConfigProto.getInstance();

                modules = new ModuleIO[] {
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificBLConfigProto.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificFRConfigProto.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificBLConfigProto.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificBLConfigProto.getInstance())
                };
                
                gyroIO = new GyroIOPigeon2();
                PhoenixOdometryThread.getInstance().start();
                break;

            case SIM:
                drivetrainConfig = SwerveDrivetrainConfigSim.getInstance();
                moduleGeneralConfig = SwerveModuleGeneralConfigSim.getInstance();

                modules = new ModuleIO[] {
                    new ModuleIOSim(moduleGeneralConfig, 0),
                    new ModuleIOSim(moduleGeneralConfig, 1),
                    new ModuleIOSim(moduleGeneralConfig, 2),
                    new ModuleIOSim(moduleGeneralConfig, 3)
                };

                gyroIO = new GyroIO() {};
                break;

            case REPLAY:
                drivetrainConfig = SwerveDrivetrainConfigComp.getInstance();
                moduleGeneralConfig = SwerveModuleGeneralConfigComp.getInstance();

                modules = new ModuleIO[] {
                    new ModuleIO() {},
                    new ModuleIO() {},
                    new ModuleIO() {},
                    new ModuleIO() {}
                };

                gyroIO = new GyroIOPigeon2();

                break;

            default:
                drivetrainConfig = SwerveDrivetrainConfigComp.getInstance();
                moduleGeneralConfig = SwerveModuleGeneralConfigComp.getInstance();

                modules = new ModuleIO[] {
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificFLConfigComp.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificFRConfigComp.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificBLConfigComp.getInstance()),
                    new ModuleIOTalonFX(moduleGeneralConfig, SwerveModuleSpecificBRConfigComp.getInstance())
                };
                
                gyroIO = new GyroIOPigeon2();
                PhoenixOdometryThread.getInstance().start();

                break;
                
        }

        kinematics = new SwerveDriveKinematics(
            drivetrainConfig.getFrontLeftPositionMeters(),
            drivetrainConfig.getFrontRightPositionMeters(),
            drivetrainConfig.getBackLeftPositionMeters(),
            drivetrainConfig.getBackRightPositionMeters()
        );

        // Create the SysId routine - this is going to be in torque current foc units not voltage
        driveCharacterizationSysIdRoutine = new SysIdRoutine(
            new SysIdRoutine.Config(
                Volts.of(1.5).per(Second), Volts.of(12), Seconds.of(15), // Use default config
                (state) -> Logger.recordOutput("DriveCharacterizationSysIdRoutineState", state.toString())
            ),
            new SysIdRoutine.Mechanism(
                (torqueCurrentFOC) -> {
                    for (ModuleIO module : modules) {
                        module.setDriveTorqueCurrentFOC(torqueCurrentFOC.in(Volts), new Rotation2d(0));
                    }
                },
                null, // No log consumer, since data is recorded by AdvantageKit
                this
            )
        );

        steerCharacterizationSysIdRoutine = new SysIdRoutine(
            new SysIdRoutine.Config(
                Volts.of(1).per(Second), Volts.of(7), Seconds.of(10), // Use default config
                (state) -> Logger.recordOutput("SteerCharacterizationSysIdRoutineState", state.toString())
            ),
            new SysIdRoutine.Mechanism(
                (torqueCurrentFOC) -> {
                    for (ModuleIO module : modules) {
                        module.setSteerTorqueCurrentFOC(torqueCurrentFOC.in(Volts), 0);
                    }
                },
                null, // No log consumer, since data is recorded by AdvantageKit
                this
            )
        );
    }

    @Override
    public void periodic() {
        double dt = Timer.getTimestamp() - prevLoopTime; 
        prevLoopTime = Timer.getTimestamp();

        Logger.recordOutput("SwerveDrive/dtPeriodic", dt);

        // Thread safe reading of the gyro and swerve inputs.
        // The read lock is released only after inputs are written via the write lock
        odometryLock.lock();
        try {
            Logger.recordOutput("SwerveDrive/stateLockAcquired", true);
            gyroIO.updateInputs(gyroInputs);
            Logger.processInputs("SwerveDrive/gyro", gyroInputs);

            for (int i = 0; i < 4; i++) {
                modules[i].updateInputs(moduleInputs[i]);
                Logger.processInputs("SwerveDrive/module" + i, moduleInputs[i]);
            }
        } finally {
            odometryLock.unlock();
        }


        SwerveModulePosition[] modulePositions = new SwerveModulePosition[4];
        SwerveModuleState[] moduleStates = new SwerveModuleState[4];

        for (int i = 0; i < 4; i++) {
            moduleStates[i] = new SwerveModuleState(
                moduleInputs[i].driveVelocityMetersPerSec,
                moduleInputs[i].steerPosition
            );
        }

        double[] odometryTimestampsSeconds = moduleInputs[0].odometryTimestampsSeconds;
        for (int i = 0; i < odometryTimestampsSeconds.length; i++) {
            for (int j = 0; j < 4; j++) {
                modulePositions[j] = new SwerveModulePosition(
                    moduleInputs[j].odometryDrivePositionsMeters[i],
                    moduleInputs[j].odometrySteerPositions[i]
                );
            }
            
            RobotState.getInstance().addOdometryObservation(
                new OdometryObservation(
                    odometryTimestampsSeconds[i],
                    gyroInputs.isConnected,
                    modulePositions,
                    moduleStates,
                    gyroInputs.isConnected ? gyroInputs.odometryYawPositions[i] : new Rotation2d(),
                    gyroInputs.isConnected ? gyroInputs.yawVelocityRadPerSec : 0
                )
            );
        }

        Logger.recordOutput("SwerveDrive/measuredModuleStates", moduleStates);
        Logger.recordOutput("SwerveDrive/measuredModulePositions", modulePositions);

        ChassisSpeeds desiredFieldRelativeSpeeds = ChassisSpeeds.fromRobotRelativeSpeeds(desiredRobotRelativeSpeeds, RobotState.getInstance().getEstimatedPose().getRotation());
        Logger.recordOutput("SwerveDrive/desiredFieldRelativeSpeeds", desiredFieldRelativeSpeeds);
        Logger.recordOutput("SwerveDrive/desiredRobotRelativeSpeeds", desiredRobotRelativeSpeeds);
        
        // Limit acceleration to prevent sudden changes in speed
        obtainableFieldRelativeSpeeds = limitAcceleration(desiredFieldRelativeSpeeds, obtainableFieldRelativeSpeeds, dt);
        Logger.recordOutput("SwerveDrive/obtainableFieldRelativeSpeeds", obtainableFieldRelativeSpeeds);

        ChassisSpeeds obtainableRobotRelativeSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(obtainableFieldRelativeSpeeds, RobotState.getInstance().getEstimatedPose().getRotation());
        Logger.recordOutput("SwerveDrive/obtainableRobotRelativeSpeeds", obtainableRobotRelativeSpeeds);

        SwerveModuleState[] moduleSetpoints = kinematics.toSwerveModuleStates(obtainableRobotRelativeSpeeds);

        SwerveDriveKinematics.desaturateWheelSpeeds(
            moduleSetpoints, 
            drivetrainConfig.getMaxModuleVelocity()
        );
        Logger.recordOutput("SwerveDrive/desaturatedModuleSetpoints", moduleSetpoints);

        for (int i = 0; i < 4; i++) {
            moduleSetpoints[i].optimize(moduleStates[i].angle);
            modules[i].setState(moduleSetpoints[i]);
        }
        Logger.recordOutput("SwerveDrive/optimizedModuleSetpoints", moduleSetpoints);


        Logger.recordOutput("SwerveDrive/CurrentCommand", this.getCurrentCommand() == null ? "" : this.getCurrentCommand().toString());
    }

    private ChassisSpeeds limitAcceleration(ChassisSpeeds desiredFieldRelativeSpeeds, ChassisSpeeds lastFieldRelativeSpeeds, double dt) {
        double desiredAcceleration = Math.hypot(
            desiredFieldRelativeSpeeds.vxMetersPerSecond - lastFieldRelativeSpeeds.vxMetersPerSecond,
            desiredFieldRelativeSpeeds.vyMetersPerSecond - lastFieldRelativeSpeeds.vyMetersPerSecond
        ) / dt;
        Logger.recordOutput("SwerveDrive/desiredAcceleration", desiredAcceleration);

        double obtainableAcceleration = MathUtil.clamp(
            desiredAcceleration,
            0,
            drivetrainConfig.getMaxTranslationalAccelerationMetersPerSecSec()
        );
        Logger.recordOutput("SwerveDrive/obtainableAcceleration", obtainableAcceleration);

        double theta = Math.atan2(
            desiredFieldRelativeSpeeds.vyMetersPerSecond - lastFieldRelativeSpeeds.vyMetersPerSecond,
            desiredFieldRelativeSpeeds.vxMetersPerSecond - lastFieldRelativeSpeeds.vxMetersPerSecond
        );

        double desiredOmegaAcceleration = (desiredFieldRelativeSpeeds.omegaRadiansPerSecond - lastFieldRelativeSpeeds.omegaRadiansPerSecond) / dt;
        Logger.recordOutput("SwerveDrive/desiredOmegaAcceleration", desiredOmegaAcceleration);

        double obtainableOmegaAcceleration = MathUtil.clamp(
            desiredOmegaAcceleration,
            -drivetrainConfig.getMaxAngularAccelerationRadiansPerSecSec(),
            drivetrainConfig.getMaxAngularAccelerationRadiansPerSecSec()
        );
        Logger.recordOutput("SwerveDrive/obtainableOmegaAcceleration", obtainableOmegaAcceleration);

        return new ChassisSpeeds(
            lastFieldRelativeSpeeds.vxMetersPerSecond + Math.cos(theta) * obtainableAcceleration * dt,
            lastFieldRelativeSpeeds.vyMetersPerSecond + Math.sin(theta) * obtainableAcceleration * dt,
            lastFieldRelativeSpeeds.omegaRadiansPerSecond + obtainableOmegaAcceleration * dt
        );
    }

    private ChassisSpeeds compensateRobotRelativeSpeeds(ChassisSpeeds speeds) {
        Rotation2d angularVelocity = new Rotation2d(speeds.omegaRadiansPerSecond * drivetrainConfig.getRotationCompensationCoefficient());
        if (angularVelocity.getRadians() != 0.0) {
            speeds = ChassisSpeeds.fromFieldRelativeSpeeds(
                ChassisSpeeds.fromRobotRelativeSpeeds( // why should this be split into two?
                    speeds.vxMetersPerSecond,
                    speeds.vyMetersPerSecond,
                    speeds.omegaRadiansPerSecond,
                    RobotState.getInstance().getEstimatedPose().getRotation().plus(angularVelocity)
                ),
                RobotState.getInstance().getEstimatedPose().getRotation()
            );
        }

        return speeds;
    }
    
    public void driveRobotRelative(ChassisSpeeds speeds) {
        desiredRobotRelativeSpeeds = speeds;
    }

    public void driveFieldRelative(ChassisSpeeds speeds) {
        speeds = ChassisSpeeds.fromFieldRelativeSpeeds(speeds, RobotState.getInstance().getEstimatedPose().getRotation());
        driveRobotRelative(speeds);
    }

    public void resetGyro(Rotation2d yaw) {
        gyroIO.resetGyro(yaw);
    }

    public Command getDynamicDriveCharacterizationSysIdRoutine(Direction direction) {
        return driveCharacterizationSysIdRoutine.dynamic(direction);
    }

    public Command getDynamicSteerCharacterizationSysIdRoutine(Direction direction) {
        return steerCharacterizationSysIdRoutine.dynamic(direction);
    }

    public Command getQuasistaticDriveCharacterizationSysIdRoutine(Direction direction) {
        return driveCharacterizationSysIdRoutine.quasistatic(direction);
    }

    public Command getQuasistaticSteerCharacterizationSysIdRoutine(Direction direction) {
        return steerCharacterizationSysIdRoutine.quasistatic(direction);
    }
}
